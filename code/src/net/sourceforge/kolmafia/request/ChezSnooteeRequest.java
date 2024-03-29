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

package net.sourceforge.kolmafia.request;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestThread;

import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.persistence.ConcoctionDatabase;
import net.sourceforge.kolmafia.persistence.ItemDatabase;

import net.sourceforge.kolmafia.utilities.StringUtilities;

public class ChezSnooteeRequest
	extends CafeRequest
{
	private static AdventureResult dailySpecial = null;

	private static final Pattern SPECIAL_PATTERN =
		Pattern.compile( "Today's Special:.*?name=whichitem value=(\\d+).*?onclick='descitem\\(\"(\\d+)\".*?<td>(.*?) \\([\\d,]+ Meat\\)</td>", Pattern.DOTALL );

	public static final AdventureResult getDailySpecial()
	{
		if ( KoLConstants.restaurantItems.isEmpty() )
		{
			ChezSnooteeRequest.getMenu();
		}

		return ChezSnooteeRequest.dailySpecial;
	}

	public ChezSnooteeRequest()
	{
		super( "Chez Snoot&eacute;e", "1" );
	}

	public ChezSnooteeRequest( final String name )
	{
		super( "Chez Snoot&eacute;e", "1" );
		this.isPurchase = true;

		int itemId = 0;
		int price = 0;

		switch ( KoLConstants.restaurantItems.indexOf( name ) )
		{
		case 0:
			itemId = -1;
			price = 50;
			break;

		case 1:
			itemId = -2;
			price = 75;
			break;

		case 2:
			itemId = -3;
			price = 100;
			break;

		case 3:
			itemId = ItemDatabase.getItemId( name );
			price = Math.max( 1, Math.abs( ItemDatabase.getPriceById( itemId ) ) ) * 3;
			break;
		}

		this.setItem( name, itemId, price );
	}

	@Override
	public void run()
	{
		if ( !KoLCharacter.canadiaAvailable() )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You can't find " + this.name );
			return;
		}

		super.run();
	}

	@Override
	public void processResults()
	{
		if ( this.isPurchase )
		{
			super.processResults();
			return;
		}

		// If we are just visiting, parse the response to find the daily special
		ChezSnooteeRequest.parseResponse( this.getURLString(), this.responseText );
	}

	protected void parseResponse()
	{
		ChezSnooteeRequest.parseResponse( this.getURLString(), this.responseText );
	}

	public static void parseResponse( final String urlString, final String responseText )
	{
		// cafe.php?cafeid=1&pwd&action=CONSUME%21&whichitem=806
		if ( !urlString.startsWith( "cafe.php" ) || !urlString.contains( "cafeid=1" ) )
		{
			return;
		}

		// If we were not attempting to consume an item, look for daily special
		if ( !urlString.contains( "action=CONSUME" ) )
		{
			Matcher matcher = ChezSnooteeRequest.SPECIAL_PATTERN.matcher( responseText );
			if ( matcher.find() )
			{
				int itemId = StringUtilities.parseInt( matcher.group( 1 ) );
				String descId = matcher.group( 2 );
				String itemName = matcher.group( 3 );
				String match = ItemDatabase.getItemDataName( itemId );
				if ( match == null || !match.equals( itemName ) )
				{
					ItemDatabase.registerItem( itemId, itemName, descId );
				}
				ChezSnooteeRequest.dailySpecial = ItemPool.get( itemId );

			}
			return;
		}

		// If consumption failed, nothing to do
		if ( !responseText.contains( "You gain" ) )
		{
			return;
		}

		AdventureResult item = ItemPool.get( GenericRequest.getWhichItem( urlString ), 1 );

		// Handle food helpers and adjust fullness, if necessary
		EatItemRequest.handleFoodHelper( item.getName(), 1, responseText );
	}

	public static final boolean onMenu( final String name )
	{
		return KoLConstants.restaurantItems.contains( name );
	}

	public static final void getMenu()
	{
		if ( !KoLCharacter.canadiaAvailable() || KoLCharacter.inZombiecore() || KoLCharacter.isJarlsberg() )
		{
			return;
		}

		KoLConstants.restaurantItems.clear();

		CafeRequest.addMenuItem( KoLConstants.restaurantItems, "Peche a la Frog", 50 );
		CafeRequest.addMenuItem( KoLConstants.restaurantItems, "As Jus Gezund Heit", 75 );
		CafeRequest.addMenuItem( KoLConstants.restaurantItems, "Bouillabaise Coucher Avec Moi", 100 );

		RequestThread.postRequest( new ChezSnooteeRequest() );

		if ( ChezSnooteeRequest.dailySpecial != null )
		{
			int itemId = ChezSnooteeRequest.dailySpecial.getItemId();
			String name = ChezSnooteeRequest.dailySpecial.getName();
			int price = Math.max( 1, Math.abs( ItemDatabase.getPriceById( itemId ) ) ) * 3;
			CafeRequest.addMenuItem( KoLConstants.restaurantItems, name, price );
		}

		ConcoctionDatabase.getUsables().sort();
		KoLmafia.updateDisplay( "Menu retrieved." );
	}

	public static final void reset()
	{
		CafeRequest.reset( KoLConstants.restaurantItems );
	}

	private static final String cafeItemName( final int itemId )
	{
		switch ( itemId )
		{
		case -1:
			return "Peche a la Frog";
		case -2:
			return "As Jus Gezund Heit";
		case -3:
			return "Bouillabaise Coucher Avec Moi";
		default:
			return ItemDatabase.getItemName( itemId );
		}
	}

	public static final boolean registerRequest( final String urlString )
	{
		Matcher matcher = CafeRequest.CAFE_PATTERN.matcher( urlString );
		if ( !matcher.find() || !matcher.group( 1 ).equals( "1" ) )
		{
			return false;
		}

		matcher = CafeRequest.ITEM_PATTERN.matcher( urlString );
		if ( !matcher.find() )
		{
			return true;
		}

		int itemId = StringUtilities.parseInt( matcher.group( 1 ) );
		String itemName;
		int price;

		switch ( itemId )
		{
		case -1:
			itemName = "Peche a la Frog";
			price = 50;
			break;
		case -2:
			itemName = "As Jus Gezund Heit";
			price = 75;
			break;
		case -3:
			itemName = "Bouillabaise Coucher Avec Moi";
			price = 100;
			break;
		default:
			itemName = ItemDatabase.getItemName( itemId );
			price = Math.max( 1, Math.abs( ItemDatabase.getPriceById( itemId ) ) ) * 3;
			break;
		}

		CafeRequest.registerItemUsage( itemName, price );
		return true;
	}
}
