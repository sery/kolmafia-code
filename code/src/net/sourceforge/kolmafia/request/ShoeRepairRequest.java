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

import java.util.Map;

import java.util.regex.Pattern;

import net.java.dev.spellcast.utilities.LockableListModel;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.CoinmasterData;

import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.persistence.CoinmastersDatabase;

import net.sourceforge.kolmafia.preferences.Preferences;

import net.sourceforge.kolmafia.session.QuestManager;

public class ShoeRepairRequest
	extends CoinMasterRequest
{
	public static final String master = "Legitimate Shoe Repair, Inc."; 

	private static final LockableListModel<AdventureResult> buyItems = CoinmastersDatabase.getBuyItems( ShoeRepairRequest.master );
	private static final Map<Integer, Integer> buyPrices = CoinmastersDatabase.getBuyPrices( ShoeRepairRequest.master );
	private static Map<Integer, Integer> itemRows = CoinmastersDatabase.getRows( ShoeRepairRequest.master );
	private static final Pattern CHRONER_PATTERN = Pattern.compile( "([\\d,]+) Chroner" );
	public static final AdventureResult CHRONER = ItemPool.get( ItemPool.CHRONER, 1 );

	public static final CoinmasterData SHOE_REPAIR =
		new CoinmasterData(
			ShoeRepairRequest.master,
			"shoeshop",
			ShoeRepairRequest.class,
			"Chroner",
			"no Chroner",
			false,
			ShoeRepairRequest.CHRONER_PATTERN,
			ShoeRepairRequest.CHRONER,
			null,
			ShoeRepairRequest.itemRows,
			"shop.php?whichshop=shoeshop",
			"buyitem",
			ShoeRepairRequest.buyItems,
			ShoeRepairRequest.buyPrices,
			null,
			null,
			null,
			null,
			"whichrow",
			GenericRequest.WHICHROW_PATTERN,
			"quantity",
			GenericRequest.QUANTITY_PATTERN,
			null,
			null,
			true
			);

	public ShoeRepairRequest()
	{
		super( ShoeRepairRequest.SHOE_REPAIR );
	}

	public ShoeRepairRequest( final boolean buying, final AdventureResult [] attachments )
	{
		super( ShoeRepairRequest.SHOE_REPAIR, buying, attachments );
	}

	public ShoeRepairRequest( final boolean buying, final AdventureResult attachment )
	{
		super( ShoeRepairRequest.SHOE_REPAIR, buying, attachment );
	}

	public ShoeRepairRequest( final boolean buying, final int itemId, final int quantity )
	{
		super( ShoeRepairRequest.SHOE_REPAIR, buying, itemId, quantity );
	}

	@Override
	public void run()
	{
		if ( this.action != null )
		{
			this.addFormField( "pwd" );
		}

		super.run();
	}

	@Override
	public void processResults()
	{
		ShoeRepairRequest.parseResponse( this.getURLString(), this.responseText );
	}

	public static void parseResponse( final String location, final String responseText )
	{
		if ( !location.contains( "whichshop=shoeshop" ) )
		{
			return;
		}

		if ( responseText.contains( "That store isn't there anymore." ) )
		{
			QuestManager.handleTimeTower( false );
			return;
		}
		
		QuestManager.handleTimeTower( true );

		CoinmasterData data = ShoeRepairRequest.SHOE_REPAIR;

		String action = GenericRequest.getAction( location );
		if ( action != null )
		{
			CoinMasterRequest.parseResponse( data, location, responseText );
			return;
		}

		// Parse current coin balances
		CoinMasterRequest.parseBalance( data, responseText );
	}

	public static String accessible()
	{
		if ( !Preferences.getBoolean( "timeTowerAvailable" ) )
		{
			return "You can't get to the Shoe Repair Shop";
		}
		return null;
	}

	public static final boolean registerRequest( final String urlString )
	{
		if ( !urlString.startsWith( "shop.php" ) || !urlString.contains( "whichshop=shoeshop" ) )
		{
			return false;
		}

		CoinmasterData data = ShoeRepairRequest.SHOE_REPAIR;
		return CoinMasterRequest.registerRequest( data, urlString, true );
	}
}
