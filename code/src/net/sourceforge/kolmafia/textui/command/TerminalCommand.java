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

import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestThread;

import net.sourceforge.kolmafia.preferences.Preferences;

import net.sourceforge.kolmafia.request.TerminalRequest;

public class TerminalCommand
	extends AbstractCommand
{
	public TerminalCommand()
	{
		this.usage = " enhance|enquiry|educate|extrude [filename] - Run the specified Source Terminal file";
	}

	@Override
	public void run( final String cmd, final String parameters )
	{
		String[] params = parameters.trim().split( "\\s+" );
		if ( params.length < 2 ) return; // Do something better here

		String command = params[0];
		String input = params[1];
		String output;

		if ( input.equals( "" ) )
		{
			output = command;
		}
		if ( command.equals( "enhance" ) )
		{
			int enhanceLimit = 1;
			String chips = Preferences.getString( "sourceTerminalChips" );
			String files = Preferences.getString( "sourceTerminalEnhanceKnown" );
			if ( chips.contains( "CRAM" ) ) enhanceLimit++;
			if ( chips.contains( "SCRAM" ) ) enhanceLimit++;
			if ( Preferences.getInteger( "_sourceTerminalEnhanceUses" ) >= enhanceLimit )
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR, "Source Terminal enhance limit reached" );
				return;
			}

			if ( input.startsWith( "item" ) )
			{
				output = "enhance items.enh";
			}
			else if ( input.startsWith( "init" ) )
			{
				output = "enhance init.enh";
			}
			else if ( input.startsWith( "meat" ) )
			{
				output = "enhance meat.enh";
			}
			else if ( input.startsWith( "sub" ) && files.contains( "substats.enh" ) )
			{
				output = "enhance substats.enh";
			}
			else if ( input.startsWith( "damage" ) && files.contains( "damage.enh" ) )
			{
				output = "enhance damage.enh";
			}
			else if ( input.startsWith( "crit" ) && files.contains( "critical.enh" ) )
			{
				output = "enhance critical.enh";
			}
			else
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR, input + " is not a valid enhance target." );
				return;
			}
		}
		else if ( command.equals( "enquiry" ) )
		{
			String files = Preferences.getString( "sourceTerminalEnquiryKnown" );

			if ( input.startsWith( "fam" ) )
			{
				output = "enquiry familiar.enq";
			}
			else if ( input.startsWith( "mon" ) )
			{
				output = "enquiry monsters.enq";
			}
			else if ( input.startsWith( "protect" ) && files.contains( "protect.enq" ) )
			{
				output = "enquiry protect.enq";
			}
			else if ( input.startsWith( "stat" ) && files.contains( "stats.enq" ) )
			{
				output = "enquiry stats.enq";
			}
			else
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR, input + " is not a valid enquiry target." );
				return;
			}
		}
		else if ( command.equals( "educate" ) )
		{
			String files = Preferences.getString( "sourceTerminalEducateKnown" );

			if ( input.startsWith( "compr" ) && files.contains( "compress.edu" ) )
			{
				output = "educate compress.edu";
			}
			else if ( input.startsWith( "digit" ) )
			{
				output = "educate digitize.edu";
			}
			else if ( input.startsWith( "dup" ) && files.contains( "duplicate.edu" ) )
			{
				output = "educate duplicate.edu";
			}
			else if ( input.startsWith( "extr" ) )
			{
				output = "educate extract.edu";
			}
			else if ( input.startsWith( "port" ) && files.contains( "portscan.edu" ) )
			{
				output = "educate portscan.edu";
			}
			else if ( input.startsWith( "turbo" ) && files.contains( "turbo.edu" ) )
			{
				output = "educate turbo.edu";
			}
			else
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR, input + " is not a valid educate target." );
				return;
			}
		}
		else if ( command.equals( "extrude" ) )
		{
			if ( Preferences.getInteger( "_sourceTerminalExtrudes" ) >= 3 )
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR, "Source Terminal extrude limit reached" );
				return;
			}

			String files = Preferences.getString( "sourceTerminalExtrudeKnown" );

			if ( input.startsWith( "booze" ) || input.contains( "gibson" ) )
			{
				output = "extrude -f booze.ext";
			}
			else if ( input.startsWith( "food" ) || input.contains( "cookie" ) )
			{
				output = "extrude -f food.ext";
			}
			else if ( input.startsWith( "fam" ) && files.contains( "familiar.ext" ) )
			{
				output = "extrude -f familiar.ext";
			}
			else if ( input.startsWith( "goggles" ) )
			{
				output = "extrude -f goggles.ext";
			}
			else if ( input.startsWith( "cram" ) && files.contains( "cram.ext" ) )
			{
				output = "extrude -f cram.ext";
			}
			else if ( input.startsWith( "dram" ) && files.contains( "dram.ext" ) )
			{
				output = "extrude -f dram.ext";
			}
			else if ( input.startsWith( "gram" ) && files.contains( "gram.ext" ) )
			{
				output = "extrude -f gram.ext";
			}
			else if ( input.startsWith( "pram" ) && files.contains( "pram.ext" ) )
			{
				output = "extrude -f pram.ext";
			}
			else if ( input.startsWith( "spam" ) && files.contains( "spam.ext" ) )
			{
				output = "extrude -f spam.ext";
			}
			else if ( input.startsWith( "tram" ) && files.contains( "tram.ext" ) )
			{
				output = "extrude -f tram.ext";
			}
			else
			{
				KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR, input + " is not a valid extrude target." );
				return;
			}
		}
		else
		{
			KoLmafia.updateDisplay( KoLConstants.MafiaState.ERROR, command + " is not a valid terminal command." );
			return;
		}

		RequestThread.postRequest( new TerminalRequest( output ) );
	}
}
