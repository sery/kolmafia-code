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
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.RequestThread;

import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.preferences.Preferences;

import net.sourceforge.kolmafia.request.TelescopeRequest;

import net.sourceforge.kolmafia.session.SorceressLairManager;

public class TelescopeCommand
	extends AbstractCommand
{
	public TelescopeCommand()
	{
		this.usage = " [look] high | low - get daily buff, or Lair hints from your telescope.";
	}

	@Override
	public void run( final String cmd, final String parameters )
	{
		// Find out how good our telescope is.
		KoLCharacter.setTelescope( false );
		int upgrades = KoLCharacter.getTelescopeUpgrades();

		if ( KoLCharacter.inBadMoon() && !KoLCharacter.kingLiberated() && upgrades > 0 )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "Your telescope is unavailable in Bad Moon." );
			return;
		}

		if ( upgrades < 1 )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You don't have a telescope." );
			return;
		}

		String[] split = parameters.split( " " );
		String command = split[ 0 ];

		if ( command.equals( "look" ) )
		{
			if ( split.length < 2 )
			{
				KoLmafia.updateDisplay( MafiaState.ERROR, "Syntax: telescope [look] high|low" );
				return;
			}

			command = split[ 1 ];
		}

		if ( command.equals( "high" ) )
		{
			RequestThread.postRequest( new TelescopeRequest( TelescopeRequest.HIGH ) );
			return;
		}

		if ( KoLCharacter.inBugcore() )
		{
			KoLmafia.updateDisplay( "You see the base of the Bugbear Mothership." );
			return;
		}

		if ( command.equals( "low" ) )
		{
			RequestThread.postRequest( new TelescopeRequest( TelescopeRequest.LOW ) );
			upgrades = KoLCharacter.getTelescopeUpgrades();
		}
		else
		{
			// Make sure we've looked through the telescope since we last ascended
			KoLCharacter.checkTelescope();
		}

		RequestLogger.printLine( "You have a telescope with " + ( upgrades - 1 ) + " additional upgrades" );

		int max = upgrades >= 5 ? 5 : upgrades;
		for ( int i = 0; i <= max; ++i )
		{
			String challenge = SorceressLairManager.getChallengeName( i );
			String test = i == 0 ? "" : Preferences.getString( "nsChallenge" + i );
			String description = SorceressLairManager.getChallengeDescription( i, test );
			RequestLogger.printLine( challenge + ": " + description );
		}
	}
}
