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

package net.sourceforge.kolmafia.webui;

import net.sourceforge.kolmafia.preferences.Preferences;

import net.sourceforge.kolmafia.utilities.StringUtilities;

public abstract class MoneyMakingGameDecorator
{
	public static final void decorate( final String location, final StringBuffer buffer )
	{
		// <input type=checkbox name=confirm>
		if ( Preferences.getBoolean( "mmgAutoConfirmBets" ) )
		{
			StringUtilities.globalStringReplace( buffer, "name=confirm", "name=confirm checked" );
		}

		String minimum = Preferences.getString( "mmgSearchMinimum" );
		String maximum = Preferences.getString( "mmgSearchMaximum" );

		if ( minimum.equals( "" ) && maximum.equals ("" ) )
		{
			return;
		}

		// <input name=lower size=9> and <input name=higher size=9>
		if ( !minimum.equals( "" ) )
		{
			String search = "name=lower";
			StringUtilities.singleStringReplace( buffer, search, search + " value= " + minimum  );
		}

		if ( !maximum.equals( "" ) )
		{
			String search = "name=higher";
			StringUtilities.singleStringReplace( buffer, search, search + " value= " + maximum  );
		}
	}

	public static final void setLimits( final String minimum, final String maximum )
	{
		Preferences.setString( "mmgSearchMinimum", minimum );
		Preferences.setString( "mmgSearchMaximum", maximum );
	}
}
