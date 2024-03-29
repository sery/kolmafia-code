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

package net.sourceforge.kolmafia.swingui;

import javax.swing.ToolTipManager;

import net.sourceforge.kolmafia.ImageCachingEditorKit;
import net.sourceforge.kolmafia.RequestEditorKit;

import net.sourceforge.kolmafia.request.GenericRequest;

public class DescriptionFrame
	extends RequestFrame
{
	private static DescriptionFrame INSTANCE = null;

	public DescriptionFrame( final String title )
	{
		super( title );
		this.mainDisplay.setEditorKit( new ImageCachingEditorKit() );
		ToolTipManager.sharedInstance().registerComponent( this.mainDisplay );
	}

	public DescriptionFrame()
	{
		this( "Documentation" );
		DescriptionFrame.INSTANCE = this;
	}

	@Override
	public boolean hasSideBar()
	{
		return false;
	}

	public static final void showLocation( final String location )
	{
		DescriptionFrame.showRequest( RequestEditorKit.extractRequest( location ) );
	}

	public static final void showRequest( final GenericRequest request )
	{
		if ( DescriptionFrame.INSTANCE == null )
		{
			GenericFrame.createDisplay( DescriptionFrame.class );
		}

		DescriptionFrame.INSTANCE.refresh( request );
	}
}
