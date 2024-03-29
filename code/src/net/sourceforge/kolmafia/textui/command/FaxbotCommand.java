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

import java.util.List;

import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.RequestLogger;

import net.sourceforge.kolmafia.persistence.FaxBotDatabase;
import net.sourceforge.kolmafia.persistence.FaxBotDatabase.FaxBot;
import net.sourceforge.kolmafia.persistence.FaxBotDatabase.Monster;

import net.sourceforge.kolmafia.swingui.FaxRequestFrame;

public class FaxbotCommand
	extends AbstractCommand
{
	public FaxbotCommand()
	{
		this.usage = " [command] - send the command to faxbot";
	}
	
	@Override
	public void run( final String cmd, final String command )
	{	
		FaxBotDatabase.configure();

		for ( FaxBot bot : FaxBotDatabase.faxbots )
		{
			if ( bot == null )
			{
				continue;
			}
			String botName = bot.getName();
			if ( botName == null )
			{
				continue;
			}

			List commands = bot.findMatchingCommands( command );
			if ( commands.isEmpty() )
			{
				continue;
			}

			if ( commands.size() > 1 )
			{
				RequestLogger.printList( commands );
				RequestLogger.printLine();

				KoLmafia.updateDisplay( MafiaState.ERROR, "[" + command + "] has too many matches in bot " + botName );
				return;
			}

			if ( !FaxRequestFrame.isBotOnline( botName ) )
			{
				continue;
			}

			Monster monster = bot.getMonsterByCommand( (String)commands.get( 0 ) );
			FaxRequestFrame.requestFax( botName, monster, false );
			return;
		}
		KoLmafia.updateDisplay( KoLConstants.MafiaState.ABORT, "No faxbots accept that command." );
	}
}
