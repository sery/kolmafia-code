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

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.RequestThread;

import net.sourceforge.kolmafia.persistence.ItemFinder;

import net.sourceforge.kolmafia.request.DisplayCaseRequest;

import net.sourceforge.kolmafia.session.DisplayCaseManager;

public class DisplayCaseCommand
	extends AbstractCommand
{
	public DisplayCaseCommand()
	{
		this.usage = " [<filter>] | put <item>... | take <item>... - list or manipulate your display case.";
	}

	@Override
	public void run( final String cmd, final String parameters )
	{
		if ( !DisplayCaseManager.collectionRetrieved )
		{
			RequestThread.postRequest( new DisplayCaseRequest() );
		}

		if ( parameters.length() == 0 )
		{
			RequestLogger.printList( KoLConstants.collection );
			return;
		}

		String itemName = parameters;
		List sourceList = null;

		if ( parameters.startsWith( "put " ) )
		{
			itemName = parameters.substring( 4 );
			sourceList = KoLConstants.inventory;
		}
		
		if ( parameters.startsWith( "take " ) )
		{
			itemName = parameters.substring( 5 );
			sourceList = KoLConstants.collection;
		}
		
		if ( sourceList == null )
		{
			ShowDataCommand.show( "display " + parameters );
			return;
		}

		AdventureResult[] items = ItemFinder.getMatchingItemList( itemName, sourceList );

		if ( items.length == 0 )
		{
			return;
		}

		RequestThread.postRequest( new DisplayCaseRequest( items, ( sourceList == KoLConstants.inventory ) ) );
	}
}
