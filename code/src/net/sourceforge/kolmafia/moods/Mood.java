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

package net.sourceforge.kolmafia.moods;

import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;

import net.java.dev.spellcast.utilities.SortedListModel;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.KoLConstants;

public class Mood
	implements Comparable<Mood>
{
	private String name;
	private List<String> parentNames;
	private SortedListModel<MoodTrigger> localTriggers;

	public Mood( String name )
	{
		this.name = name;
		this.parentNames = new ArrayList<String>();
		
		int extendsIndex = this.name.indexOf( " extends " );
		
		if ( extendsIndex != -1 )
		{
			String parentString = this.name.substring( extendsIndex + 9 );
			
			String[] parentNameArray = parentString.split( "\\s*,\\s*" );
			
			for ( String parentName : parentNameArray )
			{
				this.parentNames.add( this.getName( parentName ) );
			}
			
			this.name = this.getName( this.name.substring( 0, extendsIndex ) );
		}
		else if ( this.name.contains( "," ) )
		{
			this.name = "";
			
			String[] parentNameArray = name.split( "\\s*,\\s*" );
			
			for ( String parentName : parentNameArray )
			{
				this.parentNames.add( this.getName( parentName ) );
			}
		}
		else
		{
			this.name = this.getName( this.name );
		}

		this.localTriggers = new SortedListModel<MoodTrigger>();
	}

	public String getName()
	{
		return this.name;
	}

	public void copyFrom( Mood copyFromMood )
	{
		this.parentNames.clear();
		this.parentNames.addAll( copyFromMood.parentNames );

		this.localTriggers.addAll( copyFromMood.localTriggers );
	}
	
	public List<String> getParentNames()
	{
		return this.parentNames;
	}
	
	public void setParentNames( List<String> parentNames )
	{
		this.parentNames.clear();
		this.parentNames.addAll( parentNames );

	}

	public boolean isExecutable()
	{
		return !this.name.equals( "apathetic" ) && !this.getTriggers().isEmpty();
	}
	
	public List<MoodTrigger> getTriggers()
	{
		ArrayList<MoodTrigger> triggers = new ArrayList<MoodTrigger>();
		
		for ( String parentName : this.parentNames )
		{
			List<MoodTrigger> parentTriggers = MoodManager.getTriggers( parentName );
				
			triggers.removeAll( parentTriggers );
			triggers.addAll( parentTriggers );
		}
		
		triggers.removeAll( this.localTriggers );
		triggers.addAll( this.localTriggers );
		
		return triggers;
	}
	
	public boolean isTrigger( AdventureResult effect )
	{
		for ( MoodTrigger trigger : this.getTriggers() )
		{
			if ( trigger.matches( effect ) )
			{
				return true;
			}
		}

		return false;
	}
	
	public boolean addTrigger( MoodTrigger trigger )
	{
		if ( this.name.equals( "apathetic" ) )
		{
			return false;
		}
		
		this.localTriggers.remove( trigger );
		this.localTriggers.add( trigger );
		
		return true;
	}
	
	public boolean removeTrigger( MoodTrigger trigger )
	{
		if ( !this.localTriggers.contains( trigger ) )
		{
			return false;
		}
		
		this.localTriggers.remove( trigger );
		return true;
	}
	
	public String toSettingString()
	{
		if ( this.name.equals( "" ) )
		{
			return "";
		}
		
		StringBuilder buffer = new StringBuilder();
		buffer.append( "[ " );
		buffer.append( this.toString() );
		buffer.append( " ]" );
		buffer.append( KoLConstants.LINE_BREAK );
		
		for ( MoodTrigger trigger : this.localTriggers )
		{
			buffer.append( trigger.toSetting() );
			buffer.append( KoLConstants.LINE_BREAK );
		}
		
		return buffer.toString();
	}

	@Override
	public String toString()
	{
		StringBuilder buffer = new StringBuilder();

		buffer.append( this.name );
		
		if ( !this.parentNames.isEmpty() )
		{
			if ( !this.name.equals( "" ) )
			{
				buffer.append( " extends " );
			}
			
			boolean first = true;
			for ( String parentName : this.parentNames )
			{
				if ( first )
				{
					first = false;
				}
				else
				{
					buffer.append( ", " );
				}

				buffer.append( parentName );
			}
		}
		
		return buffer.toString();
	}
	
	@Override
	public int hashCode()
	{
		return this.name.hashCode();
	}

	public int compareTo( Mood o )
	{
		if ( o == null || !( o instanceof Mood ) )
		{
			return 1;
		}
		
		Mood m = (Mood) o;
		
		return this.name.compareTo( m.name );
	}
	
	@Override
	public boolean equals( Object o )
	{
		if ( o == null || !( o instanceof Mood ) )
		{
			return false;
		}
		
		Mood m = (Mood) o;
		
		return this.name.equals( m.name );
	}

	private String getName( String moodName )
	{
		if ( moodName == null || moodName.length() == 0 || moodName.equals( "clear" ) || moodName.equals( "autofill" ) || moodName.startsWith( "exec" ) || moodName.startsWith( "repeat" ) )
		{
			return "default";
		}

		return Pattern.compile( "[\\s,]+" ).matcher( moodName ).replaceAll( "" ).toLowerCase();
	}
}
