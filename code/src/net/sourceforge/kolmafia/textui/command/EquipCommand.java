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

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.FamiliarData;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestThread;

import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.persistence.ItemDatabase;
import net.sourceforge.kolmafia.persistence.ItemFinder;

import net.sourceforge.kolmafia.request.EquipmentRequest;

import net.sourceforge.kolmafia.session.EquipmentManager;

public class EquipCommand
	extends AbstractCommand
{
	public EquipCommand()
	{
		this.usage = " [list <filter>] | [<slot>] <item> - show equipment, or equip item [in slot].";
	}

	@Override
	public void run( final String cmd, final String parameters )
	{
		EquipCommand.equip( parameters );
	}

	public static void equip( String parameters )
	{
		parameters = parameters.toLowerCase();

		if ( parameters.length() == 0 )
		{
			ShowDataCommand.show( "equipment" );
			return;
		}

		if ( parameters.startsWith( "list" ) )
		{
			ShowDataCommand.show( "equipment " + parameters.substring( 4 ).trim() );
			return;
		}

		if ( parameters.indexOf( "(no change)" ) != -1 )
		{
			return;
		}

		// Look for name of slot
		String command = parameters.split( " " )[ 0 ];
		int slot = EquipmentRequest.slotNumber( command );

		if ( slot != -1 )
		{
			parameters = parameters.substring( command.length() ).trim();
		}

		AdventureResult match = ItemFinder.getFirstMatchingItem( parameters, ItemFinder.EQUIP_MATCH );
		if ( match == null )
		{
			return;
		}

		int itemId = match.getItemId();

		switch ( itemId )
		{
		case ItemPool.SPELUNKY_SPRING_BOOTS:
		case ItemPool.SPELUNKY_SPIKED_BOOTS:
			// Spelunky only has one "accessory" slot
			slot = EquipmentManager.ACCESSORY1;
			break;
		}

		// If he didn't specify slot name, decide where this item goes.
		if ( slot == -1 )
		{
			// If it's already equipped anywhere, give up
			for ( int i = 0; i <= EquipmentManager.FAMILIAR; ++i )
			{
				AdventureResult item = EquipmentManager.getEquipment( i );
				if ( item != null && item.getName().toLowerCase().indexOf( parameters ) != -1 )
				{
					return;
				}
			}

			// It's not equipped. Choose a slot for it
			slot = EquipmentRequest.chooseEquipmentSlot( match.getItemId() );

			// If it can't be equipped, give up
			if ( slot == -1 )
			{
				KoLmafia.updateDisplay( MafiaState.ERROR, "You can't equip a " + match.getName() );
				return;
			}
		}
		else // See if desired item is already in selected slot
		if ( EquipmentManager.getEquipment( slot ).equals( match ) )
		{
			return;
		}

		// We now know which slot the equipment will go into. See if we
		// can equip the item there.
		if ( slot == EquipmentManager.FAMILIAR )
		{
			FamiliarData familiar = KoLCharacter.getFamiliar();
			if ( familiar == FamiliarData.NO_FAMILIAR )
			{
				KoLmafia.updateDisplay( MafiaState.ERROR, "You have no familiar with you." );
				return;
			}
			if ( !familiar.canEquip( match ) )
			{
				KoLmafia.updateDisplay( MafiaState.ERROR, "Your " + familiar.getRace() + " can't wear a " + match.getName() );
				return;
			}
		}
		else if ( !EquipmentManager.canEquip( itemId ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You can't equip a " + match.getName() );
			return;
		}

		RequestThread.postRequest( new EquipmentRequest( match, slot ) );
	}
}
