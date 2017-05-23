/**
 * Copyright (c) 2005-2017, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia.persistence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLConstants.CraftingType;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.KoLmafiaCLI;
import net.sourceforge.kolmafia.RequestLogger;

import net.sourceforge.kolmafia.objectpool.IntegerPool;
import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.preferences.Preferences;

import net.sourceforge.kolmafia.request.CombineMeatRequest;
import net.sourceforge.kolmafia.request.CreateItemRequest;

import net.sourceforge.kolmafia.utilities.StringUtilities;

public class ItemFinder
{
	public static final int ANY_MATCH = 1;
	public static final int FOOD_MATCH = 2;
	public static final int BOOZE_MATCH = 3;
	public static final int SPLEEN_MATCH = 4;
	public static final int USE_MATCH = 5;
	public static final int CREATE_MATCH = 6;
	public static final int UNTINKER_MATCH = 7;
	public static final int EQUIP_MATCH = 8;
	public static final int CANDY_MATCH = 9;
	public static final int ABSORB_MATCH = 10;
	public static final int ROBO_MATCH = 11;

	public static final List<String> getMatchingNames( String searchString )
	{
		return ItemDatabase.getMatchingNames( searchString );
	}

	public static final String getFirstMatchingItemName( List<String> nameList, String searchString )
	{
		return ItemFinder.getFirstMatchingItemName( nameList, searchString, ItemFinder.ANY_MATCH );
	}

	public static final String getFirstMatchingItemName( List<String> nameList, String searchString, int filterType )
	{
		if ( nameList == null || nameList.isEmpty() )
		{
			return null;
		}

		// Filter the list
		ItemFinder.filterNameList( nameList, filterType );
		if ( nameList.isEmpty() )
		{
			return null;
		}

		// If there are multiple matches, such that one is a substring of the
		// others, choose the shorter one, on the grounds that the user would
		// have included part of the unique section of the longer name if that
		// was the item they actually intended.	 This makes it easier to refer
		// to non-clockwork in-a-boxes, and DoD potions by flavor.
		while ( nameList.size() >= 2 )
		{
			String name0 = nameList.get( 0 );
			String name1 = nameList.get( 1 );
			if ( name0.contains( name1 ) )
			{
				nameList.remove( 0 );
			}
			else if ( name1.contains( name0 ) )
			{
				nameList.remove( 1 );
			}
			else break;
		}

		// If a single item remains, that's it!
		if ( nameList.size() == 1 )
		{
			return ItemDatabase.getCanonicalName( nameList.get( 0 ) );
		}

		// Remove duplicate names that all refer to the same item?
		Set<Integer> itemIdSet = new HashSet<Integer>();
		int pseudoItems = 0;

		for ( int i = 0; i < nameList.size(); ++i )
		{
			int itemId = ItemDatabase.getItemId( nameList.get( i ) );
			if ( itemId == -1 )
			{
				pseudoItems += 1;
			}
			else
			{
				itemIdSet.add( IntegerPool.get( itemId ) );
			}
		}

		if ( ( pseudoItems + itemIdSet.size() ) == 1 )
		{
			return ItemDatabase.getCanonicalName( nameList.get( 0 ) );
		}

		String itemName;
		String rv = null;

		// Candy hearts, snowcones and cupcakes take precedence over
		// all the other items in the game, IF exactly one such item
		// matches.

		for ( int i = 0; i < nameList.size(); ++i )
		{
			itemName = nameList.get( i );
			if ( !itemName.startsWith( "pix" ) && itemName.endsWith( "candy heart" ) )
			{
				if ( rv != null ) return "";
				rv = ItemDatabase.getCanonicalName( itemName );
			}
		}

		for ( int i = 0; i < nameList.size(); ++i )
		{
			itemName = nameList.get( i );
			if ( !itemName.startsWith( "abo" ) && !itemName.startsWith( "yel" ) && itemName.endsWith( "snowcone" ) )
			{
				if ( rv != null ) return "";
				rv = ItemDatabase.getCanonicalName( itemName );
			}
		}

		for ( int i = 0; i < nameList.size(); ++i )
		{
			itemName = nameList.get( i );
			if ( itemName.endsWith( "cupcake" ) )
			{
				if ( rv != null ) return "";
				rv = ItemDatabase.getCanonicalName( itemName );
			}
		}

		if ( rv != null ) return rv;

		// If we get here, there is not a single matching item
		return "";
	}

	private static final void filterNameList( List<String> nameList, int filterType )
	{
		if ( filterType != ItemFinder.FOOD_MATCH &&
		     filterType != ItemFinder.BOOZE_MATCH &&
		     filterType != ItemFinder.SPLEEN_MATCH &&
		     filterType != ItemFinder.CANDY_MATCH )
		{
			// First, check to see if there are an HP/MP restores
			// in the list of matches.  If there are, only return
			// the restorative items (the others are irrelevant).

			ArrayList<String> restoreList = new ArrayList<String>();

			for ( int i = 0; i < nameList.size(); ++i )
			{
				String itemName = nameList.get( i );
				int itemId = ItemDatabase.getItemId( itemName );

				if ( RestoresDatabase.isRestore( itemId ) )
				{
					restoreList.add( itemName );
				}
			}

			if ( !restoreList.isEmpty() )
			{
				nameList.clear();
				nameList.addAll( restoreList );
			}
		}

		// Check for consumption filters when matching against the
		// item name.

		Iterator<String> nameIterator = nameList.iterator();

		while ( nameIterator.hasNext() )
		{
			String itemName = nameIterator.next();
			int itemId = ItemDatabase.getItemId( itemName );

			if ( filterType == ItemFinder.CREATE_MATCH || filterType == ItemFinder.UNTINKER_MATCH )
			{
				CraftingType mixMethod = ConcoctionDatabase.getMixingMethod( itemId, itemName );
				boolean condition =
					( filterType == ItemFinder.CREATE_MATCH ) ?
					( mixMethod == CraftingType.NOCREATE && CombineMeatRequest.getCost( itemId ) == 0 ) :
					( mixMethod != CraftingType.COMBINE && mixMethod != CraftingType.JEWELRY );
				ItemFinder.conditionalRemove( nameIterator, condition );
				continue;
			}

			int useType = ItemDatabase.getConsumptionType( itemId );

			switch ( filterType )
			{
			case ItemFinder.FOOD_MATCH:
				ItemFinder.conditionalRemove( nameIterator, useType != KoLConstants.CONSUME_EAT
					&& useType != KoLConstants.CONSUME_FOOD_HELPER );
				break;
			case ItemFinder.BOOZE_MATCH:
				ItemFinder.conditionalRemove( nameIterator, useType != KoLConstants.CONSUME_DRINK
					&& useType != KoLConstants.CONSUME_DRINK_HELPER );
				break;
			case ItemFinder.SPLEEN_MATCH:
				ItemFinder.conditionalRemove( nameIterator, useType != KoLConstants.CONSUME_SPLEEN );
				break;
			case ItemFinder.EQUIP_MATCH:
				switch ( useType )
				{
				case KoLConstants.EQUIP_FAMILIAR:
				case KoLConstants.EQUIP_ACCESSORY:
				case KoLConstants.EQUIP_HAT:
				case KoLConstants.EQUIP_PANTS:
				case KoLConstants.EQUIP_SHIRT:
				case KoLConstants.EQUIP_WEAPON:
				case KoLConstants.EQUIP_OFFHAND:
				case KoLConstants.EQUIP_CONTAINER:
				case KoLConstants.CONSUME_STICKER:
				case KoLConstants.CONSUME_CARD:
				case KoLConstants.CONSUME_FOLDER:
				case KoLConstants.CONSUME_BOOTSKIN:
				case KoLConstants.CONSUME_BOOTSPUR:
				case KoLConstants.CONSUME_SIXGUN:
					break;

				default:
					nameIterator.remove();
				}

				break;
			case ItemFinder.CANDY_MATCH:
				ItemFinder.conditionalRemove( nameIterator, !ItemDatabase.isCandyItem( itemId ) );
				break;

			case ItemFinder.ABSORB_MATCH:
				ItemFinder.conditionalRemove( nameIterator, ( ItemDatabase.getNoobSkillId( itemId ) == -1 &&
					!( ItemDatabase.isEquipment( itemId ) && !ItemDatabase.isFamiliarEquipment( itemId ) ) ) );
				break;

			case ItemFinder.ROBO_MATCH:
				ItemFinder.conditionalRemove( nameIterator, itemId < ItemPool.LITERAL_GRASSHOPPER || itemId > ItemPool.PHIL_COLLINS
					|| Preferences.getString( "_roboDrinks" ).contains( itemName ) );
				break;

			case ItemFinder.USE_MATCH:
				ItemFinder.conditionalRemove( nameIterator, !ItemDatabase.isUsable( itemId ) );
				break;
			}
		}

		if ( nameList.size() == 1 || filterType == ItemFinder.CREATE_MATCH || filterType == ItemFinder.UNTINKER_MATCH )
		{
			return;
		}

		// Never match against (non-quest) untradeable items not available
		// in NPC stores when other items are possible.
		// This can be overridden by adding "matchable" as a secondary
		// use; this is needed for untradeables that do need to be
		// explicitly referred to, and have names similar to other items
		// (such as the NS Tower keys).

		// If this process results in filtering EVERYTHING in our list, that's not helpful.
		// Make a backup of nameList to restore from in such a case.
		List<String> nameListCopy = new ArrayList<String>(nameList);

		nameIterator = nameList.iterator();

		while ( nameIterator.hasNext() )
		{
			String itemName = nameIterator.next();
			int itemId = ItemDatabase.getItemId( itemName );

			conditionalRemove( nameIterator, itemId != -1 &&
				!ItemDatabase.getAttribute( itemId,
					ItemDatabase.ATTR_TRADEABLE | ItemDatabase.ATTR_MATCHABLE | ItemDatabase.ATTR_QUEST ) &&
				!NPCStoreDatabase.contains( itemId ) );
		}

		// restore from last step iff we filtered _everything_
		if ( nameList.isEmpty() )
		{
			nameList.addAll( nameListCopy );
		}
	}

	private static final void conditionalRemove( Iterator<String> iterator, boolean condition )
	{
		if ( condition )
		{
			iterator.remove();
		}
	}

	/**
	 * Utility method which determines the first item which matches the given parameter string. Note that the string may
	 * also specify an item quantity before the string.
	 */

	public static final AdventureResult getFirstMatchingItem( String parameters )
	{
		return ItemFinder.getFirstMatchingItem( parameters, true, null, ItemFinder.ANY_MATCH );
	}

	public static final AdventureResult getFirstMatchingItem( String parameters, int filterType )
	{
		return ItemFinder.getFirstMatchingItem( parameters, true, null, filterType );
	}

	public static final AdventureResult getFirstMatchingItem( String parameters, boolean errorOnFailure )
	{
		return ItemFinder.getFirstMatchingItem( parameters, errorOnFailure, null, ItemFinder.ANY_MATCH );
	}

	public static final AdventureResult getFirstMatchingItem( String parameters, boolean errorOnFailure, int filterType )
	{
		return getFirstMatchingItem( parameters, errorOnFailure, null, filterType );
	}

	public static final AdventureResult getFirstMatchingItem( String parameters, boolean errorOnFailure, List<AdventureResult> sourceList, int filterType )
	{
		// Ignore spaces and tabs in front of the parameter string
		parameters = parameters.trim();

		// If there are no valid strings passed in, return
		if ( parameters.length() == 0 )
		{
			if ( errorOnFailure )
			{
				KoLmafia.updateDisplay( MafiaState.ERROR, "Need to provide an item to match." );
			}

			return null;
		}

		// Find the item id

		int itemCount = 1;
		int itemId = -1;

		// Allow the person to ask for all of the item from the source
		if ( parameters.charAt( 0 ) == '*' )
		{
			itemCount = 0;
			parameters = parameters.substring( 1 ).trim();
		}

		List<String> matchList;

		if ( parameters.contains( "\u00B6" ) || parameters.contains( "[" ) )
		{
			// At least one item is specified by item ID
			if ( parameters.contains( "," ) )
			{
				// We can't parse multiple items of this sort
				if ( errorOnFailure )
				{
					KoLmafia.updateDisplay( MafiaState.ERROR, "More than one item specified by item ID." );
				}
				return null;
			}

			int spaceIndex = parameters.indexOf( ' ' );
			if ( spaceIndex != -1 )
			{
				String itemCountString = parameters.substring( 0, spaceIndex );

				if ( StringUtilities.isNumeric( itemCountString ) )
				{
					itemCount = StringUtilities.parseInt( itemCountString );
					parameters = parameters.substring( spaceIndex + 1 ).trim();
				}
			}

			// KoL has an item whose name includes a pilcrow
			// character. Handle it
			String name = parameters;

			// If the pilcrow character is first, it is followed by an item ID
			if ( name.startsWith( "\u00B6" ) )
			{
				itemId = StringUtilities.parseInt( parameters.substring( 1 ) );
			}
			else if ( name.startsWith( "[" ) )
			{
				int index = name.indexOf( "]" );
				if ( index == -1 )
				{
					return null;
				}
				itemId = StringUtilities.parseInt( name.substring( 1, index ) );
			}
			else if ( ItemDatabase.getItemId( parameters, 1 ) == -1 )
			{
				// This is not the item with a pilcrow character
				if ( errorOnFailure )
				{
					KoLmafia.updateDisplay( MafiaState.ERROR, "Unknown item " + name );
				}
				return null;
			}

			matchList = new ArrayList<String>();
			if ( itemId != -1 )
			{
				matchList.add( "[" + itemId + "]" );
			}
			else
			{
				matchList.add( name );
			}
		}
		else if ( ItemDatabase.getItemId( parameters, 1 ) != -1 )
		{
			// The entire parameter is a single item
			matchList = new ArrayList<String>();
			matchList.add( ItemDatabase.getCanonicalName( ItemDatabase.getItemId( parameters, 1 ) ) );
		}
		else
		{
			int spaceIndex = parameters.indexOf( ' ' );

			if ( spaceIndex != -1 )
			{
				String itemCountString = parameters.substring( 0, spaceIndex );

				if ( StringUtilities.isNumeric( itemCountString ) )
				{
					itemCount = StringUtilities.parseInt( itemCountString );
					parameters = parameters.substring( spaceIndex + 1 ).trim();
				}
			}

			// This is not right for "1 seal tooth, 2 turtle totem, 3 stolen accordion"
			// since the first count is trimmed off
			matchList = ItemFinder.getMatchingNames( parameters );
		}

		String itemName = ItemFinder.getFirstMatchingItemName( matchList, parameters, filterType );

		if ( itemName == null )
		{
			if ( errorOnFailure )
			{
				String error;
				switch ( filterType )
				{
				case ANY_MATCH:
				default:
					error = " has no matches.";
					break;
				case FOOD_MATCH:
					error = " cannot be eaten.";
					break;
				case BOOZE_MATCH:
					error = " cannot be drunk.";
					break;
				case SPLEEN_MATCH:
					error = " cannot be chewed.";
					break;
				case USE_MATCH:
					error = " cannot be used.";
					break;
				case CREATE_MATCH:
					error = " cannot be created.";
					break;
				case UNTINKER_MATCH:
					error = " cannot be untinkered.";
					break;
				case EQUIP_MATCH:
					error = " cannot be equipped.";
					break;
				case CANDY_MATCH:
					error = " is not candy.";
					break;
				case ABSORB_MATCH:
					error = " cannot be absorbed.";
					break;
				}

				KoLmafia.updateDisplay( MafiaState.ERROR, "[" + parameters + "]" + error );
			}

			return null;
		}

		if ( itemName.equals( "" ) )
		{
			if ( errorOnFailure )
			{
				RequestLogger.printList( matchList );
				RequestLogger.printLine();

				KoLmafia.updateDisplay( MafiaState.ERROR, "[" + parameters + "] has too many matches." );
			}

			return null;
		}

		AdventureResult firstMatch = null;
		if ( itemId != -1 )
		{
			firstMatch = ItemPool.get( itemId, itemCount );
		}
		else
		{
			firstMatch = ItemPool.get( itemName, itemCount );
		}

		// The result also depends on the number of items which
		// are available in the given match area.

		int matchCount;

		if ( filterType == ItemFinder.CREATE_MATCH )
		{
			boolean skipNPCs = Preferences.getBoolean( "autoSatisfyWithNPCs" ) && itemCount <= 0;

			if ( skipNPCs )
			{
				// Let '*' and negative counts be interpreted
				// relative to the quantity that can be created
				// with on-hand ingredients.

				Preferences.setBoolean( "autoSatisfyWithNPCs", false );
				ConcoctionDatabase.refreshConcoctionsNow();
			}

			CreateItemRequest instance = CreateItemRequest.getInstance( firstMatch );
			matchCount = instance == null ? 0 : instance.getQuantityPossible();

			if ( skipNPCs )
			{
				Preferences.setBoolean( "autoSatisfyWithNPCs", true );
				ConcoctionDatabase.refreshConcoctionsNow();
			}
		}
		else if ( sourceList == null )
		{
			// Default to number in inventory if count was "*" (all)
			// or negative (all but that many) and no list was given.
			matchCount = itemCount <= 0 ? firstMatch.getCount( KoLConstants.inventory ) : 1;
		}
		else
		{
			matchCount = firstMatch.getCount( sourceList );
		}

		// If the person wants all except a certain quantity, update
		// the item count.

		if ( itemCount <= 0 )
		{
			itemCount = matchCount + itemCount;
			firstMatch = firstMatch.getInstance( itemCount );
		}
		else if ( matchCount < itemCount && sourceList != null )
		{
			if ( errorOnFailure )
			{
				KoLmafia.updateDisplay( MafiaState.ERROR, "[" + ( itemCount > 1 ? itemCount + " " : "" ) + firstMatch.getName() + "] requested, but " + ( matchCount == 0 ? "none" : "only " + matchCount ) + " available." );
			}

			return null;
		}

		if ( KoLmafiaCLI.isExecutingCheckOnlyCommand )
		{
			KoLmafia.updateDisplay( firstMatch == null ? "No match" : firstMatch.toString() );
			return null;
		}

		return itemCount <= 0 ? null : firstMatch;
	}

	public static AdventureResult[] getMatchingItemList( String itemList )
	{
		return ItemFinder.getMatchingItemList( itemList, true, null, ItemFinder.ANY_MATCH );
	}

	public static AdventureResult[] getMatchingItemList( String itemList, boolean errorOnFailure )
	{
		return ItemFinder.getMatchingItemList( itemList, errorOnFailure, null, ItemFinder.ANY_MATCH );
	}

	public static AdventureResult[] getMatchingItemList( String itemList, List<AdventureResult> sourceList )
	{
		return ItemFinder.getMatchingItemList( itemList, true, sourceList, ItemFinder.ANY_MATCH );
	}

	public static AdventureResult[] getMatchingItemList( String itemList, boolean errorOnFailure, List<AdventureResult> sourceList )
	{
		return ItemFinder.getMatchingItemList( itemList, errorOnFailure, sourceList, ItemFinder.ANY_MATCH );
	}

	public static AdventureResult[] getMatchingItemList( String itemList, boolean errorOnFailure, List<AdventureResult> sourceList, int filterType )
	{
		AdventureResult firstMatch = ItemFinder.getFirstMatchingItem( itemList, false, sourceList, filterType );
		if ( firstMatch != null )
		{
			AdventureResult[] items = new AdventureResult[ 1 ];
			items[ 0 ] = firstMatch;
			return items;
		}

		String[] itemNames = itemList.split( "\\s*,\\s*" );

		boolean isMeatMatch = false;
		ArrayList<AdventureResult> items = new ArrayList<AdventureResult>();

		for ( String name : itemNames )
		{
			isMeatMatch = false;

			if ( name.endsWith( " meat" ) )
			{
				String amountString = name.substring( 0, name.length() - 5 ).trim();

				if ( amountString.equals( "*" ) || StringUtilities.isNumeric( amountString ) )
				{
					isMeatMatch = true;

					int amount = 0;

					if ( !amountString.equals( "*" ) )
					{
						amount = StringUtilities.parseInt( amountString );
					}

					if ( amount <= 0 )
					{
						amount +=
							sourceList == KoLConstants.storage ? KoLCharacter.getStorageMeat() :
							sourceList == KoLConstants.closet ? KoLCharacter.getClosetMeat() :
							KoLCharacter.getAvailableMeat();
					}

					firstMatch = new AdventureResult( AdventureResult.MEAT, amount );
				}
			}

			if ( !isMeatMatch )
			{
				firstMatch = ItemFinder.getFirstMatchingItem( name, errorOnFailure, sourceList, filterType );
			}

			if ( firstMatch != null )
			{
				AdventureResult.addResultToList( items, firstMatch );
			}
		}

		AdventureResult[] result = new AdventureResult[ items.size() ];
		return items.toArray( result );
	}
}
