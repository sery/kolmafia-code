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

import net.sourceforge.kolmafia.persistence.SkillDatabase;

public class ManaBurn
	implements Comparable<ManaBurn>
{
	private final int skillId;
	private final String skillName;

	private int duration;
	private final int limit;
	private int count;

	public ManaBurn( final int skillId, final String skillName, final int duration, final int limit )
	{
		this.skillId = skillId;
		this.skillName = skillName;
		this.duration = duration;
		this.limit = limit;
		this.count = 0;
	}
	
	public boolean isCastable( int allowedMP )
	{
		if ( this.duration >= this.limit )
		{
			return false;
		}

		// The max(1,...) guarantees that this loop will terminate.
		
		int cost = Math.max( 1, this.getMPCost() );
		
		if ( cost > allowedMP )
		{
			return false;
		}
		
		return true;
	}
	
	public int simulateCast()
	{
		++this.count;
		this.duration += SkillDatabase.getEffectDuration( this.skillId );
		
		return this.getMPCost();
	}

	public int compareTo( final ManaBurn o )
	{
		return this.duration - ( (ManaBurn) o ).duration;
	}

	private int getMPCost()
	{
		return SkillDatabase.getMPConsumptionById( this.skillId );
	}
	
	@Override
	public String toString()
	{
		return "cast " + this.count + " " + this.skillName;
	}
}