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
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION ) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE ) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia.textui.command;

import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.KoLmafiaCLI;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.RequestThread;

import net.sourceforge.kolmafia.persistence.ConsumablesDatabase;
import net.sourceforge.kolmafia.persistence.ItemDatabase;

import net.sourceforge.kolmafia.preferences.Preferences;

import net.sourceforge.kolmafia.request.ChezSnooteeRequest;
import net.sourceforge.kolmafia.request.ClanLoungeRequest;
import net.sourceforge.kolmafia.request.DrinkItemRequest;
import net.sourceforge.kolmafia.request.EatItemRequest;
import net.sourceforge.kolmafia.request.MicroBreweryRequest;
import net.sourceforge.kolmafia.request.StandardRequest;

import net.sourceforge.kolmafia.utilities.InputFieldUtilities;
import net.sourceforge.kolmafia.utilities.StringUtilities;

public class RestaurantCommand
	extends AbstractCommand
{
	public RestaurantCommand()
	{
		this.usage = "[?] [ daily special | <item> ] - show daily special [or consume it or other restaurant item].";
	}

	@Override
	public void run( final String cmd, final String parameters )
	{
		if ( cmd.equals( "restaurant" ) )
		{
			RestaurantCommand.makeChezSnooteeRequest( parameters );
		}
		else
		{
			RestaurantCommand.makeMicroBreweryRequest( parameters );
		}
	}

	public static boolean makeChezSnooteeRequest( final String parameters )
	{
		if ( !KoLCharacter.canadiaAvailable() )
		{
			KoLmafia.updateDisplay( "Since you have no access to Little Canadia, you may not visit the restaurant." );
			return false;
		}

		if ( KoLConstants.restaurantItems.isEmpty() )
		{
			ChezSnooteeRequest.getMenu();
		}

		if ( parameters.equals( "" ) )
		{
			RequestLogger.printLine( "Today's Special: " + ChezSnooteeRequest.getDailySpecial() );
			return false;
		}

		String[] splitParameters = AbstractCommand.splitCountAndName( parameters );
		String countString = splitParameters[ 0 ];
		String nameString = splitParameters[ 1 ];

		if ( nameString.equalsIgnoreCase( "daily special" ) )
		{
			nameString = ChezSnooteeRequest.getDailySpecial().getName();
		}
		else if ( nameString.startsWith( "\u00B6" ) )
		{
			String name = ItemDatabase.getItemName( StringUtilities.parseInt( nameString.substring( 1 ) ) );
			if ( name != null )
			{
				nameString = name;
			}
		}

		nameString = nameString.toLowerCase();

		for ( int i = 0; i < KoLConstants.restaurantItems.size(); ++i )
		{
			String name = (String) KoLConstants.restaurantItems.get( i );

			if ( !StringUtilities.substringMatches( name.toLowerCase(), nameString, false ) )
			{
				continue;
			}

			if ( KoLmafiaCLI.isExecutingCheckOnlyCommand )
			{
				RequestLogger.printLine( name );
				return true;
			}

			int count = countString == null || countString.length() == 0 ? 1 : StringUtilities.parseInt( countString );

			if ( count == 0 )
			{
				int fullness = ConsumablesDatabase.getFullness( name );
				if ( fullness > 0 )
				{
					count = ( KoLCharacter.getFullnessLimit() - KoLCharacter.getFullness() ) / fullness;
				}
			}

			for ( int j = 0; j < count; ++j )
			{
				RequestThread.postRequest( new ChezSnooteeRequest( name ) );
			}

			return true;
		}

		return false;
	}

	public static boolean makeMicroBreweryRequest( final String parameters )
	{
		if ( !KoLCharacter.gnomadsAvailable() )
		{
			KoLmafia.updateDisplay( "Since you have no access to the Gnomish Gnomad Camp, you may not visit the micromicrobrewery." );
			return false;
		}

		if ( KoLConstants.microbreweryItems.isEmpty() )
		{
			MicroBreweryRequest.getMenu();
		}

		if ( parameters.equals( "" ) )
		{
			RequestLogger.printLine( "Today's Special: " + MicroBreweryRequest.getDailySpecial() );
			return false;
		}

		String[] splitParameters = AbstractCommand.splitCountAndName( parameters );
		String countString = splitParameters[ 0 ];
		String nameString = splitParameters[ 1 ];

		if ( nameString.equalsIgnoreCase( "daily special" ) )
		{
			nameString = MicroBreweryRequest.getDailySpecial().getName();
		}
		else if ( nameString.startsWith( "\u00B6" ) )
		{
			String name = ItemDatabase.getItemName( StringUtilities.parseInt( nameString.substring( 1 ) ) );
			if ( name != null )
			{
				nameString = name;
			}
		}

		nameString = nameString.toLowerCase();

		for ( int i = 0; i < KoLConstants.microbreweryItems.size(); ++i )
		{
			String name = (String) KoLConstants.microbreweryItems.get( i );

			if ( !StringUtilities.substringMatches( name.toLowerCase(), nameString, false ) )
			{
				continue;
			}

			if ( KoLmafiaCLI.isExecutingCheckOnlyCommand )
			{
				RequestLogger.printLine( name );
				return true;
			}

			int count = countString == null || countString.length() == 0 ? 1 : StringUtilities.parseInt( countString );

			if ( count == 0 )
			{
				int inebriety = ConsumablesDatabase.getInebriety( name );
				if ( inebriety > 0 )
				{
					count = ( KoLCharacter.getInebrietyLimit() - KoLCharacter.getInebriety() ) / inebriety;
				}
			}

			for ( int j = 0; j < count; ++j )
			{
				RequestThread.postRequest( new MicroBreweryRequest( name ) );
			}

			return true;
		}

		return false;
	}

	public static boolean makeHotDogStandRequest( final String command, final String parameters )
	{
		String[] splitParameters = AbstractCommand.splitCountAndName( parameters );
		String hotdog = splitParameters[ 1 ];

		// We claim any request to eat a hot dog
		if ( !ClanLoungeRequest.isHotDog( hotdog ) )
		{
			return false;
		}

		if ( !StandardRequest.isAllowed( "Clan Items", "Clan hot dog stand" ) || KoLCharacter.inZombiecore() || KoLCharacter.isJarlsberg() )
		{
			KoLmafia.updateDisplay( "The clan hot dog stand is not available on your current path." );
			return true;
		}

		if ( !ClanLoungeRequest.canVisitLounge() )
		{
			KoLmafia.updateDisplay( "Since you have no access to the Clan VIP lounge, you may not visit the Hot Dog Stand." );
			return true;
		}

		if ( !ClanLoungeRequest.availableHotDog( hotdog ) )
		{
			KoLmafia.updateDisplay( "The '" + hotdog + "' is not currently available in your clan" );
			return true;
		}

		String countString = splitParameters[ 0 ];
		int count = countString == null ? 1 : StringUtilities.parseInt( countString );

		boolean isFancy = ClanLoungeRequest.isFancyHotDog( hotdog );
		if ( isFancy )
		{
			if ( Preferences.getBoolean( "_fancyHotDogEaten" ) )
			{
				KoLmafia.updateDisplay( "You've already eaten a fancy hot dog today." );
				return true;
			}
			if ( count > 1 )
			{
				KoLmafia.updateDisplay( "(You can only eat 1 fancy hot dog per day; reducing count to 1.)" );
				count = 1;
			}

			int available = KoLCharacter.getFullnessLimit() - KoLCharacter.getFullness();
			int fullness = ConsumablesDatabase.getFullness( hotdog );
			if ( fullness > available )
			{
				KoLmafia.updateDisplay( "You are too full to eat a " + hotdog );
				return true;
			}
		}

		if ( !command.equals( "eatsilent" ) && !EatItemRequest.askAboutMilk( hotdog, count ) )
		{
			return true;
		}

		if ( KoLmafiaCLI.isExecutingCheckOnlyCommand )
		{
			RequestLogger.printLine( hotdog );
			return true;
		}

		// Everything checks out. Eat it!
		for ( int j = 0; j < count; ++j )
		{
			RequestThread.postRequest( ClanLoungeRequest.buyHotDogRequest( hotdog ) );	
		}
		return true;
	}

	public static boolean makeSpeakeasyRequest( final String command, final String parameters )
	{
		String[] splitParameters = AbstractCommand.splitCountAndName( parameters );
		String speakeasyDrink = ClanLoungeRequest.findSpeakeasyDrink( splitParameters[ 1 ] );

		// We claim any request to drink a Speakeasy drink
		if ( speakeasyDrink == null )
		{
			return false;
		}

		if ( !StandardRequest.isAllowed( "Clan Items", "Clan speakeasy" ) || KoLCharacter.isJarlsberg() || KoLCharacter.inHighschool() )
		{
			KoLmafia.updateDisplay( "The clan speakeasy is not available on your current path." );
			return true;
		}

		if ( !ClanLoungeRequest.canVisitLounge() )
		{
			KoLmafia.updateDisplay( "Since you have no access to the Clan VIP lounge, you may not visit the Speakeasy." );
			return true;
		}

		// Check if you're already drunk three first, as drinks are never available in ConcoctionDatabase when you've had all three.
		int drunkCount = Preferences.getInteger( "_speakeasyDrinksDrunk" );
		if ( drunkCount >= 3 )
		{
			KoLmafia.updateDisplay( "You've already drunk three speakeasy drinks today." );
			return true;
		}

		if ( !ClanLoungeRequest.availableSpeakeasyDrink( speakeasyDrink ) )
		{
			KoLmafia.updateDisplay( "The '" + speakeasyDrink + "' is not currently available in your clan" );
			return true;
		}

		String countString = splitParameters[ 0 ];
		int count = countString == null ? 1 : StringUtilities.parseInt( countString );

		if ( count + drunkCount > 3 )
		{
			KoLmafia.updateDisplay( "(You can only drink 3 speakeasy drinks per day; reducing count to " + ( 3 - drunkCount ) + ".)" );
			count = 3 - drunkCount;
		}

		int available = KoLCharacter.getInebrietyLimit() - KoLCharacter.getInebriety();
		int inebriety = ConsumablesDatabase.getInebriety( speakeasyDrink );
		if ( inebriety > available && DrinkItemRequest.permittedOverdrink != KoLCharacter.getUserId() )
		{
			if ( ( KoLCharacter.getAdventuresLeft() > 0 ||
				KoLCharacter.getFullness() < KoLCharacter.getFullnessLimit() ) &&
				!InputFieldUtilities.confirm( "Are you sure you want to overdrink?" ) )
			{
				return true;
			}
		}

		if ( !command.equals( "drinksilent" ) && !DrinkItemRequest.askAboutOde( speakeasyDrink, inebriety, count ) )
		{
			return true;
		}

		if ( KoLmafiaCLI.isExecutingCheckOnlyCommand )
		{
			RequestLogger.printLine( speakeasyDrink );
			return true;
		}

		// Everything checks out. Eat it!
		for ( int j = 0; j < count; ++j )
		{
			RequestThread.postRequest( ClanLoungeRequest.buySpeakeasyDrinkRequest( speakeasyDrink ) );	
		}
		return true;
	}
}
