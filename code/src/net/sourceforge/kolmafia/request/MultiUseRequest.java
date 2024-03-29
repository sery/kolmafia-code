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

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLConstants.CraftingType;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.RequestLogger;

import net.sourceforge.kolmafia.objectpool.Concoction;
import net.sourceforge.kolmafia.objectpool.ConcoctionPool;
import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.persistence.ItemDatabase;

import net.sourceforge.kolmafia.session.ResultProcessor;

import net.sourceforge.kolmafia.utilities.StringUtilities;

public class MultiUseRequest
	extends CreateItemRequest
{
	private AdventureResult ingredient = null;

	public MultiUseRequest( final Concoction conc )
	{
		super( "multiuse.php", conc );

		AdventureResult[] ingredients = conc.getIngredients();

		// There must be at least one ingredient
		if ( ingredients == null )
		{
			return;
		}

		this.ingredient = ingredients[ 0 ];
		int use = this.ingredient.getItemId();
		int count = this.ingredient.getCount();

		this.addFormField( "action", "useitem" );
		this.addFormField( "quantity", String.valueOf( count ) );
		this.addFormField( "whichitem", String.valueOf( use ) );
	}

	public MultiUseRequest( final int itemId )
	{
		this( ConcoctionPool.get( itemId ) );
	}

	@Override
	public void reconstructFields()
	{
		if ( this.ingredient == null )
		{
			return;
		}

		int use = this.ingredient.getItemId();
		int count = this.ingredient.getCount();

		if ( count == 1 )
		{
			this.constructURLString( "inv_use.php" );
			this.addFormField( "which", "3" );
			this.addFormField( "whichitem", String.valueOf( use ) );
			this.addFormField( "ajax", "1" );
		}
		else
		{
			this.constructURLString( "multiuse.php" );
			this.addFormField( "action", "useitem" );
			this.addFormField( "quantity", String.valueOf( count ) );
			this.addFormField( "whichitem", String.valueOf( use ) );
		}
	}

	@Override
	public void run()
	{
		if ( KoLCharacter.inBeecore() && ItemDatabase.unusableInBeecore( this.ingredient.getItemId() ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You are too scared of bees to use " + this.ingredient.getName() + " to create " + this.getName() );
			return;
		}

		// Attempting to make the ingredients will pull the
		// needed items from the closet if they are missing.

		if ( !this.makeIngredients() )
		{
			return;
		}

		super.run();
	}

	@Override
	public void processResults()
	{
		MultiUseRequest.parseResponse( UseItemRequest.lastItemUsed, this.responseText );
		UseItemRequest.lastItemUsed = null;
	}

	public static final void parseResponse( AdventureResult item, String responseText )
	{
		int baseId = item.getItemId();
		int count = item.getCount();
		String plural = ItemDatabase.getPluralName( baseId );
		if ( responseText.contains( "You don't have that many" ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "You don't have that many " + plural );
			return;
		}
		if ( !responseText.contains( "You acquire" ) )
		{
			KoLmafia.updateDisplay( MafiaState.ERROR, "Using " + count + " " + ( count == 1 ? item.getName() : plural ) + " doesn't make anything interesting." );
			return;
		}
		Concoction concoction = ConcoctionPool.findConcoction( CraftingType.MULTI_USE, baseId, count );

		if ( concoction == null )
		{
			return;
		}

		AdventureResult[] ingredients = concoction.getIngredients();

		for ( int i = 0; i < ingredients.length; ++i )
		{
			AdventureResult ingredient = ingredients[ i ];
			ResultProcessor.processResult( ingredient.getInstance( -1 * ingredient.getCount() ) );
		}
	}

	public static final boolean registerRequest( final String urlString )
	{
		if ( !urlString.startsWith( "multiuse.php" ) && !urlString.startsWith( "inv_use.php" ) )
		{
			return false;
		}

		Matcher itemMatcher = GenericRequest.WHICHITEM_PATTERN.matcher( urlString );
		if ( !itemMatcher.find() )
		{
			return false;
		}

		int count = 1;

		if ( urlString.startsWith( "multiuse.php" ) )
		{
			Matcher quantityMatcher = GenericRequest.QUANTITY_PATTERN.matcher( urlString );
			if ( !quantityMatcher.find() )
			{
				return false;
			}
			count = StringUtilities.parseInt( quantityMatcher.group( 1 ) );
		}

		// Item ID of the base item
		int baseId = StringUtilities.parseInt( itemMatcher.group( 1 ) );

		// Find concoction made by multi-using this many of this item
		Concoction concoction = ConcoctionPool.findConcoction( CraftingType.MULTI_USE, baseId, count );

		// If this is not a concoction, let somebody else log this.
		if ( concoction == null )
		{
			return false;
		}

		AdventureResult[] ingredients = concoction.getIngredients();

		// Punt if don't have enough of any ingredient.
		for ( int i = 0; i < ingredients.length; ++i )
		{
			AdventureResult ingredient = ingredients[ i ];
			int have = ingredient.getCount( KoLConstants.inventory );
			int need = ingredient.getCount();
			if ( have < need )
			{
				return true;
			}
		}

		UseItemRequest.setLastItemUsed( ItemPool.get( baseId, count ) );

		StringBuilder text = new StringBuilder();
		text.append( "Use " );

		for ( int i = 0; i < ingredients.length; ++i )
		{
			AdventureResult ingredient = ingredients[ i ];
			int used = ingredient.getCount();
			if ( i > 0 )
			{
				text.append( " + " );
			}

			text.append( used );
			text.append( " " );
			text.append( ingredient.getName() );
		}

		RequestLogger.updateSessionLog();
		RequestLogger.updateSessionLog( text.toString() );

		return true;
	}
}
