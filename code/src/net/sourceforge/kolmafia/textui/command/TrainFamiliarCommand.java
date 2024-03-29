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

import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLmafia;

import net.sourceforge.kolmafia.swingui.FamiliarTrainingFrame;

import net.sourceforge.kolmafia.utilities.StringUtilities;

public class TrainFamiliarCommand
	extends AbstractCommand
{
	public TrainFamiliarCommand()
	{
		this.usage = " base <weight> | buffed <weight> | turns <number> - train familiar.";
	}

	@Override
	public void run( final String cmd, final String parameters )
	{
		// train (base | buffed | turns) <goal>
		String[] split = parameters.split( " " );

		if ( split.length < 2 || split.length > 3 )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "Syntax: train type goal" );
			return;
		}

		String typeString = split[ 0 ].toLowerCase();

		int type;

		if ( typeString.equals( "base" ) )
		{
			type = FamiliarTrainingFrame.BASE;
		}
		else if ( typeString.startsWith( "buff" ) )
		{
			type = FamiliarTrainingFrame.BUFFED;
		}
		else if ( typeString.equals( "turns" ) )
		{
			type = FamiliarTrainingFrame.TURNS;
		}
		else
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "Unknown training type: " + typeString );
			return;
		}

		FamiliarTrainingFrame.levelFamiliar( StringUtilities.parseInt( split[ 1 ] ), type, false );
	}
}
