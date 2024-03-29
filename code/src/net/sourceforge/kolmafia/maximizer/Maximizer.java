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

package net.sourceforge.kolmafia.maximizer;

import java.util.Collections;
import java.util.Iterator;

import net.java.dev.spellcast.utilities.LockableListModel;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.FamiliarData;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.KoLmafiaCLI;
import net.sourceforge.kolmafia.Modifiers;
import net.sourceforge.kolmafia.RequestLogger;

import net.sourceforge.kolmafia.moods.MoodManager;

import net.sourceforge.kolmafia.objectpool.Concoction;
import net.sourceforge.kolmafia.objectpool.ConcoctionPool;
import net.sourceforge.kolmafia.objectpool.EffectPool;
import net.sourceforge.kolmafia.objectpool.FamiliarPool;
import net.sourceforge.kolmafia.objectpool.ItemPool;

import net.sourceforge.kolmafia.persistence.CandyDatabase;
import net.sourceforge.kolmafia.persistence.ConsumablesDatabase;
import net.sourceforge.kolmafia.persistence.EffectDatabase;
import net.sourceforge.kolmafia.persistence.EquipmentDatabase;
import net.sourceforge.kolmafia.persistence.ItemDatabase;
import net.sourceforge.kolmafia.persistence.ItemFinder;
import net.sourceforge.kolmafia.persistence.MallPriceDatabase;
import net.sourceforge.kolmafia.persistence.QuestDatabase;
import net.sourceforge.kolmafia.persistence.QuestDatabase.Quest;
import net.sourceforge.kolmafia.persistence.SkillDatabase;

import net.sourceforge.kolmafia.preferences.Preferences;

import net.sourceforge.kolmafia.request.CampgroundRequest;
import net.sourceforge.kolmafia.request.ClanLoungeRequest;
import net.sourceforge.kolmafia.request.CreateItemRequest;
import net.sourceforge.kolmafia.request.EquipmentRequest;
import net.sourceforge.kolmafia.request.SkateParkRequest;
import net.sourceforge.kolmafia.request.StandardRequest;
import net.sourceforge.kolmafia.request.UneffectRequest;
import net.sourceforge.kolmafia.request.UseItemRequest;
import net.sourceforge.kolmafia.request.UseSkillRequest;

import net.sourceforge.kolmafia.session.EquipmentManager;
import net.sourceforge.kolmafia.session.InventoryManager;
import net.sourceforge.kolmafia.session.Limitmode;
import net.sourceforge.kolmafia.session.RabbitHoleManager;
import net.sourceforge.kolmafia.session.StoreManager;

import net.sourceforge.kolmafia.swingui.MaximizerFrame;

import net.sourceforge.kolmafia.utilities.StringUtilities;


public class Maximizer
{
	private static boolean firstTime = true;

	public static final LockableListModel<Boost> boosts = new LockableListModel<Boost>();
	public static Evaluator eval;

	public static String [] maximizationCategories =
	{
		"_hoboPower",
		"_brimstone",
		"_cloathing",
		"_slimeHate",
		"_stickers",
		"_folderholder",
		"_cardsleeve",
		"_smithsness",
	};

	static MaximizerSpeculation best;
	static int bestChecked;
	static long bestUpdate;

	public static boolean maximize( String maximizerString, int maxPrice, int priceLevel, boolean isSpeculationOnly )
	{
		MaximizerFrame.expressionSelect.setSelectedItem( maximizerString );
		int equipLevel = isSpeculationOnly ? 1 : -1;

		// iECOC has to be turned off before actually maximizing as
		// it would cause all item lookups during the process to just
		// print the item name and return null.

		KoLmafiaCLI.isExecutingCheckOnlyCommand = false;

		Maximizer.maximize( equipLevel, maxPrice, priceLevel, false );

		if ( !KoLmafia.permitsContinue() )
		{
			return false;
		}

		Modifiers mods = Maximizer.best.calculate();
		Modifiers.overrideModifier( "Generated:_spec", mods );

		return !Maximizer.best.failed;
	}

	public static void maximize( int equipLevel, int maxPrice, int priceLevel, boolean includeAll )
	{
		KoLmafia.forceContinue();
		String maxMe = (String) MaximizerFrame.expressionSelect.getSelectedItem();
		KoLConstants.maximizerMList.addItem( maxMe );
		Maximizer.eval = new Evaluator( maxMe );

		// parsing error
		if ( !KoLmafia.permitsContinue() )
		{
			return;
		}

		double current = Maximizer.eval.getScore( KoLCharacter.getCurrentModifiers() );

		if ( maxPrice <= 0 )
		{
			maxPrice = Math.min( Preferences.getInteger( "autoBuyPriceLimit" ),
					     KoLCharacter.getAvailableMeat() );
		}

		KoLmafia.updateDisplay( Maximizer.firstTime ?
			"Maximizing (1st time may take a while)..." : "Maximizing..." );
		Maximizer.firstTime = false;

		Maximizer.boosts.clear();
		if ( equipLevel != 0 )
		{
			Maximizer.best = new MaximizerSpeculation();
			Maximizer.best.getScore();
			// In case the current outfit scores better than any tried combination,
			// due to some newly-added constraint (such as +melee):
			Maximizer.best.failed = true;
			Maximizer.bestChecked = 0;
			Maximizer.bestUpdate = System.currentTimeMillis() + 5000;
			try
			{
				Maximizer.eval.enumerateEquipment( equipLevel, maxPrice, priceLevel );
			}
			catch ( MaximizerExceededException e )
			{
				Maximizer.boosts.add( new Boost( "", "(maximum achieved, no further combinations checked)", -1, null, 0.0 ) );
			}
			catch ( MaximizerLimitException e )
			{
				Maximizer.boosts.add( new Boost( "", "<font color=red>(hit combination limit, optimality not guaranteed)</font>", -1, null, 0.0 ) );
			}
			catch ( MaximizerInterruptedException e )
			{
				KoLmafia.forceContinue();
				Maximizer.boosts.add( new Boost( "", "<font color=red>(interrupted, optimality not guaranteed)</font>", -1, null, 0.0 ) );
			}
			MaximizerSpeculation.showProgress();

			boolean[] alreadyDone = new boolean[ EquipmentManager.ALL_SLOTS ];

			for ( int slot = EquipmentManager.ACCESSORY1; slot <= EquipmentManager.ACCESSORY3; ++slot )
			{
				if ( Maximizer.best.equipment[ slot ].getItemId() == ItemPool.SPECIAL_SAUCE_GLOVE &&
					EquipmentManager.getEquipment( slot ).getItemId() != ItemPool.SPECIAL_SAUCE_GLOVE )
				{
					equipLevel = Maximizer.emitSlot( slot, equipLevel, maxPrice, priceLevel, current );
					alreadyDone[ slot ] = true;
				}
			}

			for ( int slot = 0; slot < EquipmentManager.ALL_SLOTS; ++slot )
			{
				if ( !alreadyDone[ slot ] )
				{
					equipLevel = Maximizer.emitSlot( slot, equipLevel, maxPrice, priceLevel, current );
				}
			}
		}

		current = Maximizer.eval.getScore(
			KoLCharacter.getCurrentModifiers() );

		Iterator<String> i = Modifiers.getAllModifiers();
		while ( i.hasNext() )
		{
			String lookup = i.next();

			// Include skills from absorbing items in Noobcore
			if ( KoLCharacter.inNoobcore() && lookup.startsWith( "Skill:" ) )
			{
				String name = lookup.substring( 6 );
				int skillId = SkillDatabase.getSkillId( name );
				if ( skillId < 23001 || skillId > 23125 )
				{
					continue;
				}
				if ( KoLCharacter.hasSkill( skillId ) )
				{
					continue;
				}
				int absorbsLeft = KoLCharacter.getAbsorbsLimit() - KoLCharacter.getAbsorbs();
				if ( absorbsLeft < 1 )
				{
					continue;
				}
				MaximizerSpeculation spec = new MaximizerSpeculation();
				String mods = Modifiers.getModifierList( "Skill", name ).toString();
				spec.setCustom( mods );
				double delta = spec.getScore() - current;
				if ( delta <= 0.0 )
				{
					continue;
				}
				int[] itemList = ItemDatabase.getItemListByNoobSkillId( skillId );
				if ( itemList == null )
				{
					continue;
				}
				// Iterate over items to see if we have access to them
				int count = 0;
				for ( int itemId : itemList )
				{
					CheckedItem checkedItem = new CheckedItem( itemId, equipLevel, maxPrice, priceLevel );
					// We won't include unavailable items, as this just gets far too large
					String cmd, text;
					int price = 0;
					AdventureResult item = ItemPool.get( itemId );
					cmd = "absorb \u00B6" + itemId;
					text = "absorb " + item.getName() + " (" + name + ", ";
					if ( checkedItem.inventory > 0 )
					{
					}
					else if ( checkedItem.initial > 0 )
					{
						String method = InventoryManager.simRetrieveItem( item, equipLevel == -1, false );
						if ( !method.equals( "have" ) )
						{
							text = method + " & " + text;
						}
						if ( method.equals( "uncloset" ) )
						{
							cmd = "closet take 1 \u00B6" + itemId + ";" + cmd;
						}
						// Should be only hitting this after Ronin I think
						else if ( method.equals( "pull" ) ) 
						{
							cmd = "pull 1 \u00B6" + itemId + ";" + cmd;
						}
					}
					else if ( checkedItem.creatable > 0 )
					{
						text = "make & " + text;
						cmd = "make \u00B6" + itemId + ";" + cmd;
					}
					else if ( checkedItem.npcBuyable > 0 )
					{
						text = "buy & " + text;
						cmd = "buy 1 \u00B6" + itemId + ";" + cmd;
						price = ConcoctionPool.get( item ).price;
					}
					else if ( checkedItem.pullable > 0 )
					{
						text = "pull & " + text;
						cmd = "pull \u00B6" + itemId + ";" + cmd;
					}
					else if ( checkedItem.mallBuyable > 0 )
					{
						text = "acquire & " + text;
						if ( priceLevel > 0 )
						{
							price = StoreManager.getMallPrice( item );
						}
					}
					else
					{
						continue;
					}
					if ( price > 0 )
					{
						text = text + KoLConstants.COMMA_FORMAT.format( price ) +
							" meat, ";
					}
					text = text + KoLConstants.MODIFIER_FORMAT.format( delta ) + ")";
					text = text + " [" + absorbsLeft + " absorbs remaining]";
					if ( count > 0 )
					{
						text = "  or " + text;
					}
					Maximizer.boosts.add( new Boost( cmd, text, item, delta ) );
					count++;
				}
			}
			// Include enchantments from absorbing equipment in Noobcore
			else if ( KoLCharacter.inNoobcore() && lookup.startsWith( "Item:" ) )
			{
				String name = lookup.substring( 5 );
				int itemId = ItemDatabase.getItemId( name );
				int absorbsLeft = KoLCharacter.getAbsorbsLimit() - KoLCharacter.getAbsorbs();
				if ( absorbsLeft < 1 )
				{
					continue;
				}
				// Cannot abosrb undiscardable items
				if ( !ItemDatabase.isDiscardable( itemId ) )
				{
					continue;
				}
				// Can only absorb tradeable and gift items
				if ( !ItemDatabase.isTradeable( itemId ) && !ItemDatabase.isGiftItem( itemId ) )
				{
					continue;
				}
				// Can only get it from Equipment
				if ( !EquipmentDatabase.isEquipment( itemId ) )
				{
					continue;
				}
				MaximizerSpeculation spec = new MaximizerSpeculation();
				Modifiers itemMods = Modifiers.getItemModifiers( itemId );
				if ( itemMods == null )
				{
					continue;
				}
				// Only take numeric modifiers, and not Surgeonosity, from Items in Noobcore
				StringBuilder mods = new StringBuilder();
				for ( int j = 0; j < Modifiers.DOUBLE_MODIFIERS; ++j )
				{
					switch ( j )
					{
					case Modifiers.SURGEONOSITY:
						continue;
					}
					if ( itemMods.get( j ) != 0.0 )
					{
						if ( mods.length() > 0 )
						{
							mods.append( ", " );
						}
						mods.append( Modifiers.getModifierName( j ) + ": " + itemMods.get( j ) );
					}
				}
				if ( mods.length() == 0 )
				{
					continue;
				}
				spec.setCustom( mods.toString() );
				double delta = spec.getScore() - current;
				if ( delta <= 0.0 )
				{
					continue;
				}
				// Check if we have access to item
				CheckedItem checkedItem = new CheckedItem( itemId, equipLevel, maxPrice, priceLevel );
				// We won't include unavailable items, as this just gets far too large
				String cmd, text;
				int price = 0;
				AdventureResult item = ItemPool.get( itemId );
				cmd = "absorb \u00B6" + itemId;
				text = "absorb " + item.getName() + " (";
				if ( checkedItem.inventory > 0 )
				{
				}
				else if ( checkedItem.initial > 0 )
				{
					String method = InventoryManager.simRetrieveItem( item, equipLevel == -1, false );
					if ( !method.equals( "have" ) )
					{
						text = method + " & " + text;
					}
					if ( method.equals( "uncloset" ) )
					{
						cmd = "closet take 1 \u00B6" + itemId + ";" + cmd;
					}
					// Should be only hitting this after Ronin I think
					else if ( method.equals( "pull" ) ) 
					{
						cmd = "pull 1 \u00B6" + itemId + ";" + cmd;
					}
				}
				else if ( checkedItem.creatable > 0 )
				{
					text = "make & " + text;
					cmd = "make \u00B6" + itemId + ";" + cmd;
				}
				else if ( checkedItem.npcBuyable > 0 )
				{
					text = "buy & " + text;
					cmd = "buy 1 \u00B6" + itemId + ";" + cmd;
					price = ConcoctionPool.get( item ).price;
				}
				else if ( checkedItem.pullable > 0 )
				{
					text = "pull & " + text;
					cmd = "pull \u00B6" + itemId + ";" + cmd;
				}
				else if ( checkedItem.mallBuyable > 0 )
				{
					text = "acquire & " + text;
					if ( priceLevel > 0 )
					{
						price = StoreManager.getMallPrice( item );
					}
				}
				else
				{
					continue;
				}
				if ( price > 0 )
				{
					text = text + KoLConstants.COMMA_FORMAT.format( price ) +
						" meat, ";
				}
				text = text + "lasts til end of day, ";
				text = text + KoLConstants.MODIFIER_FORMAT.format( delta ) + ")";
				text = text + " [" + absorbsLeft + " absorbs remaining";
				if ( checkedItem.inventory > 0 )
				{
					text = text + ", " + checkedItem.inventory + " in inventory";
				}
				if ( checkedItem.initial - checkedItem.inventory > 0 )
				{
					text = text + ", " + ( checkedItem.initial - checkedItem.inventory ) + " obtainable";
				}
				if ( checkedItem.creatable > 0 )
				{
					text = text + ", " + checkedItem.creatable + " createable";
				}
				if ( checkedItem.npcBuyable > 0 )
				{
					text = text + ", " + checkedItem.npcBuyable + " pullable";
				}
				text = text + "]";
				Maximizer.boosts.add( new Boost( cmd, text, item, delta ) );
			}
			if ( !lookup.startsWith( "Effect:" ) )
			{
				continue;
			}
			String name = lookup.substring( 7 );
			int effectId = EffectDatabase.getEffectId( name );
			if ( effectId == -1 )
			{
				continue;
			}

			double delta;
			boolean isSpecial = false;
			MaximizerSpeculation spec = new MaximizerSpeculation();
			AdventureResult effect = EffectPool.get( effectId );
			name = effect.getName();
			boolean hasEffect = KoLConstants.activeEffects.contains( effect );
			Iterator<String> sources;
			String cmd, text;
			int price = 0;
			int advCost = 0;
			int mpCost = 0;
			int soulsauceCost = 0;
			int thunderCost = 0;
			int rainCost = 0;
			int lightningCost = 0;
			int duration = 0;
			int usesRemaining = 0;
			int itemsRemaining = 0;
			int itemsCreatable = 0;
			if ( !hasEffect )
			{
				spec.addEffect( effect );
				delta = spec.getScore() - current;
				if ( (spec.getModifiers().getRawBitmap( Modifiers.MUTEX_VIOLATIONS )
					& ~KoLCharacter.currentRawBitmapModifier( Modifiers.MUTEX_VIOLATIONS )) != 0 )
				{	// This effect creates a mutex problem that the player
					// didn't already have.  In the future, perhaps suggest
					// uneffecting the conflicting effect, but for now just skip.
					continue;
				}
				switch ( Maximizer.eval.checkConstraints(
					Modifiers.getEffectModifiers( effectId ) ) )
				{
				case -1:
					continue;
				case 0:
					if ( delta <= 0.0 ) continue;
					break;
				case 1:
					isSpecial = true;
				}
				if ( Evaluator.checkEffectConstraints( effectId ) )
				{
					continue;
				}
				sources = EffectDatabase.getAllActions( effectId );
				cmd = MoodManager.getDefaultAction( "lose_effect", name );
				if ( !sources.hasNext() )
				{
					if ( includeAll )
					{
						sources = Collections.singletonList(
							"(no known source of " + name + ")" ).iterator();
					}
					else continue;
				}
			}
			else
			{
				spec.removeEffect( effect );
				delta = spec.getScore() - current;
				switch ( Maximizer.eval.checkConstraints(
					Modifiers.getEffectModifiers( effectId ) ) )
				{
				case 1:
					continue;
				case 0:
					if ( delta <= 0.0 ) continue;
					break;
				case -1:
					isSpecial = true;
				}
				cmd = MoodManager.getDefaultAction( "gain_effect", name );
				if ( cmd.length() == 0 )
				{
					if ( includeAll )
					{
						cmd = "(find some way to remove " + name + ")";
					}
					else continue;
				}
				sources = Collections.singletonList( cmd ).iterator();
			}

			boolean haveVipKey = InventoryManager.getCount( ItemPool.VIP_LOUNGE_KEY ) > 0;
			boolean orFlag = false;
			while ( sources.hasNext() )
			{
				if ( !KoLmafia.permitsContinue() )
				{
					return;
				}
				cmd = text = sources.next();
				AdventureResult item = null;

				if ( cmd.startsWith( "#" ) )	// usage note, no command
				{
					if ( includeAll )
					{
						if ( cmd.contains( "BM" ) &&
							!KoLCharacter.inBadMoon() )
						{
							continue;	// no use displaying this in non-BM
						}
						text = (orFlag ? "(...or get " : "(get ")
							+ name + " via " + cmd.substring( 1 ) + ")";
						orFlag = false;
						cmd = "";
					}
					else continue;
				}

				if ( hasEffect &&
					!cmd.toLowerCase().contains( name.toLowerCase() ) )
				{
					text = text + " (to remove " + name + ")";
				}

				if ( cmd.startsWith( "(" ) )	// preformatted note
				{
					cmd = "";
					orFlag = false;
				}
				else if ( cmd.startsWith( "use " ) || cmd.startsWith( "chew " ) ||
					  cmd.startsWith( "drink " ) || cmd.startsWith( "eat " ) )
				{
					// Hardcoded exception for "Trivia Master", which has a non-standard use command.
					if ( cmd.contains( "use 1 Trivial Avocations Card: What?, 1 Trivial Avocations Card: When?" ) && !MoodManager.canMasterTrivia() )
					{
						continue;
					}

					// Can get Box of Sunshine in hardcore/ronin, but can't use it
					if ( !KoLCharacter.canInteract() && cmd.startsWith( "use 1 box of sunshine" ) )
					{
						continue;
					}

					String iName = cmd.substring( cmd.indexOf( " " ) + 3 ).trim();
					if ( cmd.startsWith( "use " ) )
					{
						item = ItemFinder.getFirstMatchingItem( iName, false );
					}
					else if ( cmd.startsWith( "chew " ) )
					{
						item = ItemFinder.getFirstMatchingItem( iName, false, ItemFinder.SPLEEN_MATCH );
					}
					else if ( cmd.startsWith( "drink " ) )
					{
						item = ItemFinder.getFirstMatchingItem( iName, false, ItemFinder.BOOZE_MATCH );
					}
					else if ( cmd.startsWith( "eat " ) )
					{
						item = ItemFinder.getFirstMatchingItem( iName, false, ItemFinder.FOOD_MATCH );
					}

					if ( item != null )
					{
						// Resolve bang potions and slime vials
						int itemId = item.getItemId();
						if ( itemId == -1 )
						{
							item = item.resolveBangPotion();
							itemId = item.getItemId();
						}
						if ( itemId == -1 )
						{
							continue;
						}

						Modifiers effMod = Modifiers.getItemModifiers( item.getItemId() );
						if ( effMod != null )
						{
							duration = (int) effMod.get( Modifiers.EFFECT_DURATION );
						}
					}
					// Hot Dogs don't have items
					if ( item == null && ClanLoungeRequest.isHotDog( iName ) )
					{
						if ( KoLCharacter.inBadMoon() )
						{
							continue;
						}
						else if ( !StandardRequest.isAllowed( "Clan Item", "Clan Hot Dog Stand" ) )
						{
							continue;
						}
						// Jarlsberg and Zombie characters can't eat hot dogs
						else if ( KoLCharacter.isJarlsberg() || KoLCharacter.isZombieMaster() )
						{
							continue;
						}
						else if ( Limitmode.limitClan() )
						{
							continue;
						}
						else if ( !haveVipKey )
						{
							if ( includeAll )
							{
								text = "( get access to the VIP lounge )";
								cmd = "";
							}
							else continue;
						}
						// Fullness available?
						int full = ClanLoungeRequest.hotdogNameToFullness( iName );
						if ( full > 0 &&
						     KoLCharacter.getFullness() + full > KoLCharacter.getFullnessLimit() )
						{
							continue;
						}
						// Is it Fancy and has one been used?
						if ( ClanLoungeRequest.isFancyHotDog( iName ) &&
						     Preferences.getBoolean( "_fancyHotDogEaten" ) )
						{
							continue;
						}
						else
						{
							Modifiers effMod = Modifiers.getModifiers( "Item", iName );
							if ( effMod != null )
							{
								duration = (int) effMod.get( Modifiers.EFFECT_DURATION );
							}
							usesRemaining = 1;
						}
					}
					else if ( item == null && !cmd.contains( "," ) )
					{
						if ( includeAll )
						{
							text = "(identify & " + cmd + ")";
							cmd = "";
						}
						else continue;
					}
					else if ( item != null )
					{
						int itemId = item.getItemId();
						usesRemaining = UseItemRequest.maximumUses( itemId );
						if ( usesRemaining <= 0 )
						{
							continue;
						}
					}
				}
				else if ( cmd.startsWith( "gong " ) )
				{
					item = ItemPool.get( ItemPool.GONG, 1 );
					advCost = 3;
					duration = 20;
				}
				else if ( cmd.startsWith( "cast " ) )
				{
					String skillName = UneffectRequest.effectToSkill( name );
					int skillId = SkillDatabase.getSkillId( skillName );
					mpCost = SkillDatabase.getMPConsumptionById( skillId );
					advCost = SkillDatabase.getAdventureCost( skillId );
					soulsauceCost = SkillDatabase.getSoulsauceCost( skillId );
					thunderCost = SkillDatabase.getThunderCost( skillId );
					rainCost = SkillDatabase.getRainCost( skillId );
					lightningCost = SkillDatabase.getLightningCost( skillId );
					duration = SkillDatabase.getEffectDuration( skillId );
					UseSkillRequest skill = UseSkillRequest.getUnmodifiedInstance( skillName );
					if ( skill != null )
					{
						usesRemaining = skill.getMaximumCast();
					}
					if ( !KoLCharacter.hasSkill( skillName ) || usesRemaining == 0 )
					{
						if ( includeAll )
						{
							boolean isBuff = SkillDatabase.isBuff( skillId );
							text = "(learn to " + cmd + (isBuff ? ", or get it from a buffbot)" : ")");
							cmd = "";
						}
						else continue;
					}
				}
				else if ( cmd.startsWith( "synthesize " ) )
				{
					// You must know the skill
					if ( !KoLCharacter.hasSkill( "Sweet Synthesis" ) )
					{
						if ( includeAll )
						{
							text = "(learn the Sweet Synthesis skill)";
							cmd = "";
						}
						else continue;
					}
					// You must have a spleen available
					usesRemaining = KoLCharacter.getSpleenLimit() - KoLCharacter.getSpleenUse();
					if ( usesRemaining < 1 )
					{
						cmd = "";
					}
					// You must have (or be able to get) a suitable pair of candies
					if ( CandyDatabase.synthesisPair( effectId ) == CandyDatabase.NO_PAIR )
					{
						cmd = "";
					}
					duration = 30;
				}
				else if ( cmd.startsWith( "friars " ) )
				{
					int lfc = Preferences.getInteger( "lastFriarCeremonyAscension" );
					int ka = Preferences.getInteger( "knownAscensions" );
					if ( lfc < ka || Limitmode.limitZone( "Friars" ) )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "friarsBlessingReceived" ) )
					{
						cmd = "";
					}
					duration = 20;
					usesRemaining = Preferences.getBoolean( "friarsBlessingReceived" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "hatter " ) )
				{
					boolean haveEffect = KoLConstants.activeEffects.contains( EffectPool
						.get( EffectPool.DOWN_THE_RABBIT_HOLE ) );
					boolean havePotion = InventoryManager.hasItem( ItemPool.DRINK_ME_POTION );
					if ( !havePotion && !haveEffect )
					{
						continue;
					}
					else if ( !RabbitHoleManager.hatLengthAvailable( StringUtilities.parseInt( cmd
						.substring( 7 ) ) ) )
					{
						continue;
					}
					else if ( Limitmode.limitZone( "RabbitHole" ) )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "_madTeaParty" ) )
					{
						cmd = "";
					}
					duration = 30;
					usesRemaining = Preferences.getBoolean( "_madTeaParty" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "mom " ) )
				{
					if ( !QuestDatabase.isQuestFinished( Quest.SEA_MONKEES ) )
					{
						continue;
					}
					else if ( Limitmode.limitZone( "The Sea" ) )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "_momFoodReceived" ) )
					{
						cmd = "";
					}
					duration = 50;
					usesRemaining = Preferences.getBoolean( "_momFoodReceived" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "summon " ) )
				{
					if ( !QuestDatabase.isQuestFinished( Quest.MANOR ) )
					{
						continue;
					}
					int onHand = InventoryManager.getAccessibleCount( ItemPool.EVIL_SCROLL );
					int candles = InventoryManager.getAccessibleCount( ItemPool.BLACK_CANDLE );
					int creatable = CreateItemRequest.getInstance( ItemPool.EVIL_SCROLL )
						.getQuantityPossible();

					if ( !KoLCharacter.canInteract() && ( ( onHand + creatable ) < 1 || candles < 3 ) )
					{
						continue;
					}
					else if ( Limitmode.limitZone( "Manor0" ) )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "demonSummoned" ) )
					{
						cmd = "";
					}
					else
					{
						try
						{
							int num = Integer.parseInt( cmd.split( " " )[ 1 ] );
							if ( Preferences.getString( "demonName" + num ).equals( "" ) )
							{
								cmd = "";
							}
						}
						catch ( Exception e )
						{
						}
					}
					// Existential Torment is 20 turns, but won't appear here as the effects are unknown
					duration = 30;
					usesRemaining = Preferences.getBoolean( "demonSummoned" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "concert " ) )
				{
					String side = Preferences.getString( "sidequestArenaCompleted" );
					boolean available = false;

					if ( side.equals( "none" ) )
					{
						continue;
					}
					else if ( Limitmode.limitZone( "Island" ) || Limitmode.limitZone( "IsleWar" ) )
					{
						continue;
					}
					else if ( side.equals( "fratboy" ) )
					{
						available = cmd.contains( "Elvish" ) ||
								cmd.contains( "Winklered" ) ||
								cmd.contains( "White-boy Angst" );
					}
					else if ( side.equals( "hippy" ) )
					{
						available = cmd.contains( "Moon" ) ||
								cmd.contains( "Dilated" ) ||
								cmd.contains( "Optimist" );
					}

					if ( !available )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "concertVisited" ) )
					{
						cmd = "";
					}
					duration = 20;
					usesRemaining = Preferences.getBoolean( "concertVisited" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "telescope " ) )
				{
					if ( Limitmode.limitCampground() )
					{
						continue;
					}
					else if ( Preferences.getInteger( "telescopeUpgrades" ) == 0 )
					{
						if ( includeAll )
						{
							text = "( get a telescope )";
							cmd = "";
						}
						else continue;
					}
					else if ( KoLCharacter.inBadMoon() || KoLCharacter.inNuclearAutumn() )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "telescopeLookedHigh" ) )
					{
						cmd = "";
					}
					duration = 10;
					usesRemaining = Preferences.getBoolean( "telescopeLookedHigh" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "ballpit" ) )
				{
					if ( !KoLCharacter.canInteract() )
					{
						continue;
					}
					else if ( Limitmode.limitClan() )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "_ballpit" ) )
					{
						cmd = "";
					}
					duration = 20;
					usesRemaining = Preferences.getBoolean( "_ballpit" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "jukebox" ) )
				{
					if ( !KoLCharacter.canInteract() )
					{
						continue;
					}
					else if ( Limitmode.limitClan() )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "_jukebox" ) )
					{
						cmd = "";
					}
					duration = 10;
					usesRemaining = Preferences.getBoolean( "_jukebox" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "pool " ) )
				{
					if ( KoLCharacter.inBadMoon() )
					{
						continue;
					}
					else if ( !StandardRequest.isAllowed( "Clan Item", "Pool Table" ) )
					{
						continue;
					}
					else if ( Limitmode.limitClan() )
					{
						continue;
					}
					else if ( !haveVipKey )
					{
						if ( includeAll )
						{
							text = "( get access to the VIP lounge )";
							cmd = "";
						}
						else continue;
					}
					else if ( Preferences.getInteger( "_poolGames" ) >= 3 )
					{
						cmd = "";
					}
					duration = 10;
					usesRemaining = 3 - Preferences.getInteger( "_poolGames" );
				}
				else if ( cmd.startsWith( "shower " ) )
				{
					if ( KoLCharacter.inBadMoon() )
					{
						continue;
					}
					else if ( !StandardRequest.isAllowed( "Clan Item", "April Shower" ) )
					{
						continue;
					}
					else if ( Limitmode.limitClan() )
					{
						continue;
					}
					else if ( !haveVipKey )
					{
						if ( includeAll )
						{
							text = "( get access to the VIP lounge )";
							cmd = "";
						}
						else continue;
					}
					else if ( Preferences.getBoolean( "_aprilShower" ) )
					{
						cmd = "";
					}
					duration = 50;
					usesRemaining = Preferences.getBoolean( "_aprilShower" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "swim " ) )
				{
					if ( KoLCharacter.inBadMoon() )
					{
						continue;
					}
					else if ( !StandardRequest.isAllowed( "Clan Item", "Clan Swimming Pool" ) )
					{
						continue;
					}
					else if ( Limitmode.limitClan() )
					{
						continue;
					}
					else if ( !haveVipKey )
					{
						if ( includeAll )
						{
							text = "( get access to the VIP lounge )";
							cmd = "";
						}
						else continue;
					}
					else if ( Preferences.getBoolean( "_olympicSwimmingPool" ) )
					{
						cmd = "";
					}
					duration = 50;
					usesRemaining = Preferences.getBoolean( "_olympicSwimmingPool" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "mayosoak" ) )
				{
					AdventureResult workshed = CampgroundRequest.getCurrentWorkshedItem();
					if ( KoLCharacter.inBadMoon() )
					{
						continue;
					}
					else if ( !StandardRequest.isAllowed( "Items", "portable Mayo Clinic" ) )
					{
						continue;
					}
					else if ( Limitmode.limitCampground() )
					{
						continue;
					}
					else if ( workshed == null || workshed.getItemId() != ItemPool.MAYO_CLINIC )
					{
						if ( includeAll )
						{
							text = "( install portable Mayo Clinic )";
							cmd = "";
						}
						else continue;
					}
					else if ( Preferences.getBoolean( "_mayoTankSoaked" ) )
					{
						cmd = "";
					}
					duration = 20;
					usesRemaining = Preferences.getBoolean( "_mayoTankSoaked" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "barrelprayer" ) )
				{
					if ( KoLCharacter.inBadMoon() )
					{
						continue;
					}
					else if ( !StandardRequest.isAllowed( "Items", "shrine to the Barrel god" ) )
					{
						continue;
					}
					else if ( Limitmode.limitZone( "Dungeon Full of Dungeons" ) )
					{
						continue;
					}
					else if ( !Preferences.getBoolean( "barrelShrineUnlocked" ) )
					{
						if ( includeAll )
						{
							text = "( install shrine to the Barrel god )";
							cmd = "";
						}
						else continue;
					}
					else if ( Preferences.getBoolean( "_barrelPrayer" ) )
					{
						cmd = "";
					}
					duration = 50;
					usesRemaining = Preferences.getBoolean( "_barrelPrayer" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "styx " ) )
				{
					if ( !KoLCharacter.inBadMoon() )
					{
						continue;
					}
					else if ( Limitmode.limitZone( "BadMoon" ) )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "styxPixieVisited" ) )
					{
						cmd = "";
					}
					duration = 10;
					usesRemaining = Preferences.getBoolean( "styxPixieVisited" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "skate " ) )
				{
					String status = Preferences.getString( "skateParkStatus" );
					int buff = SkateParkRequest.placeToBuff( cmd.substring( 6 ) );
					Object [] data = SkateParkRequest.buffToData( buff );
					String buffPref = (String) data[4];
					String buffStatus = (String) data[6];

					if ( !status.equals( buffStatus ) )
					{
						continue;
					}
					else if ( Limitmode.limitZone( "The Sea" ) )
					{
						continue;
					}
					else if ( Preferences.getBoolean( buffPref ) )
					{
						cmd = "";
					}
					duration = 30;
					usesRemaining = Preferences.getBoolean( buffPref ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "gap " ) )
				{
					AdventureResult pants = EquipmentManager.getEquipment( EquipmentManager.PANTS );
					if ( InventoryManager.getAccessibleCount( ItemPool.GREAT_PANTS ) == 0 )
					{
						if ( includeAll )
						{
							text = "(acquire and equip Greatest American Pants for " + name + ")";
							cmd = "";
						}
						else
						{
							continue;
						}
					}
					else if ( Preferences.getInteger( "_gapBuffs" ) >= 5 )
					{
						cmd = "";
					}
					else if ( pants == null || ( pants.getItemId() != ItemPool.GREAT_PANTS ) )
					{
						text = "(equip Greatest American Pants for " + name + ")";
						cmd = "";
					}
					if ( name.equals( "Super Skill" ) )
					{
						duration = 5;
					}
					else if ( name.equals( "Super Structure" ) || name.equals( "Super Accuracy" ) )
					{
						duration = 10;
					}
					else if ( name.equals( "Super Vision" ) || name.equals( "Super Speed" ) )
					{
						duration = 20;
					}
					usesRemaining = 5 - Preferences.getInteger( "_gapBuffs" );
				}
				else if ( cmd.startsWith( "spacegate" ) )
				{
					if ( !StandardRequest.isAllowed( "Items", "Spacegate access badge" ) )
					{
						continue;
					}
					boolean available = Preferences.getBoolean( "spacegateAlways" ) || Preferences.getBoolean( "_spacegateToday" );
					String number = cmd.substring( cmd.length() - 1 );
					String setting = "spacegateVaccine" + number;
					boolean vaccineAvailable = Preferences.getBoolean( setting );
					if ( !available || !vaccineAvailable )
					{
						if ( includeAll )
						{
							text = "(unlock Spacegate and vaccine " + number + " for " + name + ")";
							cmd = "";
						}
						else
						{
							continue;
						}
					}
					else if ( Preferences.getBoolean( "_spacegateVaccine" ) )
					{
						cmd = "";
					}
				}
				else if ( cmd.startsWith( "play" ) )
				{
					if ( InventoryManager.getAccessibleCount( ItemPool.DECK_OF_EVERY_CARD ) == 0 )
					{
						if ( includeAll )
						{
							text = "(acquire Deck of Every Card for " + name + ")";
							cmd = "";
						}
						else
						{
							continue;
						}
					}
					else if ( Preferences.getInteger( "_deckCardsDrawn" ) > 10 )
					{
						cmd = "";
					}
					duration = 20;
					usesRemaining = ( 15 - Preferences.getInteger( "_deckCardsDrawn" ) ) / 5;
				}
				else if ( cmd.startsWith( "grim" ) )
				{
					FamiliarData fam = KoLCharacter.findFamiliar( FamiliarPool.GRIM_BROTHER );
					if ( fam == null )
					{
						if ( Limitmode.limitFamiliars() )
						{
							continue;
						}
						else if ( includeAll )
						{
							text = "(get a Grim Brother familiar for " + name + ")";
							cmd = "";
						}
						else
						{
							continue;
						}
					}
					else if ( Preferences.getBoolean( "_grimBuff" ) )
					{
						cmd = "";
					}
					duration = 30;
					usesRemaining = Preferences.getBoolean( "_grimBuff" ) ? 0 : 1;
				}
				else if ( cmd.equals( "witchess" ) )
				{
					if ( !KoLConstants.campground.contains( ItemPool.get( ItemPool.WITCHESS_SET, 1 ) ) )
					{
						if ( includeAll )
						{
							text = "(install Witchess Set for " + name + ")";
							cmd = "";
						}
						else
						{
							continue;
						}
					}
					else if ( Preferences.getBoolean( "_witchessBuff" ) )
					{
						cmd = "";
					}
					else if ( Preferences.getInteger( "puzzleChampBonus" ) != 20 )
					{
						text = "(manually get " + name + ")";
						cmd = "";
					}
					duration = 25;
					usesRemaining = Preferences.getBoolean( "_witchessBuff" ) ? 0 : 1;
				}
				else if ( cmd.equals( "crossstreams" ) )
				{
					if ( InventoryManager.getAccessibleCount( ItemPool.PROTON_ACCELERATOR ) == 0 )
					{
						if ( includeAll )
						{
							text = "(acquire protonic accelerator pack and crossstreams for " + name + ")";
							cmd = "";
						}
						else
						{
							continue;
						}
					}
					else if ( Preferences.getBoolean( "_streamsCrossed" ) )
					{
						cmd = "";
					}
					duration = 10;
					usesRemaining = Preferences.getBoolean( "_streamsCrossed" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "terminal enhance" ) )
				{
					int limit = 1;
					String chips = Preferences.getString( "sourceTerminalChips" );
					String files = Preferences.getString( "sourceTerminalEnhanceKnown" );
					if ( chips.contains( "CRAM" ) ) limit++;
					if ( chips.contains( "SCRAM" ) ) limit++;
					boolean haveTerminal = KoLConstants.campground.contains( ItemPool.get( ItemPool.SOURCE_TERMINAL, 1 ) ) ||
					                       KoLConstants.falloutShelter.contains( ItemPool.get( ItemPool.SOURCE_TERMINAL, 1 ) );
					if ( !haveTerminal )
					{
						if ( includeAll )
						{
							text = "(install Source Terminal for " + name + ")";
							cmd = "";
						}
						else
						{
							continue;
						}
					}
					else if ( cmd.contains( name ) && !files.contains( name ) )
					{
						if ( includeAll )
						{
							text = "(install Source terminal file: " + name + " for " + name + ")";
							cmd = "";
						}
						else
						{
							continue;
						}
					}
					else
					{
						if ( Preferences.getInteger( "_sourceTerminalEnhanceUses" ) >= limit )
						{
							cmd = "";
						}
					}
					duration = 25 + ( chips.contains( "INGRAM" ) ? 25 : 0 ) + 5*Preferences.getInteger( "sourceTerminalPram" );
					usesRemaining = limit - Preferences.getInteger( "_sourceTerminalEnhanceUses" );
				}
				else if ( cmd.startsWith( "campground vault3" ) )
				{
					if ( !KoLCharacter.inNuclearAutumn() )
					{
						continue;
					}
					if ( Preferences.getInteger( "falloutShelterLevel" ) < 3 )
					{
						continue;
					}
					else if ( Limitmode.limitCampground() )
					{
						continue;
					}
					else if ( Preferences.getBoolean( "_falloutShelterSpaUsed" ) )
					{
						cmd = "";
					}
					duration = 100;
					usesRemaining = Preferences.getBoolean( "_falloutShelterSpaUsed" ) ? 0 : 1;
				}
				else if ( cmd.startsWith( "skeleton " ) )
				{
					item = ItemPool.get( ItemPool.SKELETON, 1 );
					duration = 30;
				}
				else if ( cmd.startsWith( "toggle" ) )
				{
					if ( !KoLConstants.activeEffects.contains( EffectPool.get( EffectPool.INTENSELY_INTERESTED ) ) &&
					     !KoLConstants.activeEffects.contains( EffectPool.get( EffectPool.SUPERFICIALLY_INTERESTED ) ) )
					{
						continue;
					}
				}

				if ( item != null )
				{
					String iname = item.getName();

					if ( KoLCharacter.inBeecore() &&
						KoLCharacter.getBeeosity( iname ) > 0 )
					{
						continue;
					}

					if ( !StandardRequest.isAllowed( "Items", iname ) )
					{
						continue;
					}

					int full = ConsumablesDatabase.getFullness( iname );
					if ( full > 0 &&
						KoLCharacter.getFullness() + full > KoLCharacter.getFullnessLimit() )
					{
						cmd = "";
					}
					full = ConsumablesDatabase.getInebriety( iname );
					if ( full > 0 &&
						KoLCharacter.getInebriety() + full > KoLCharacter.getInebrietyLimit() )
					{
						cmd = "";
					}
					full = ConsumablesDatabase.getSpleenHit( iname );
					if ( full > 0 && !cmd.contains( "chew" ) )
					{
						RequestLogger.printLine( "(Note: extender for " +
							name + " is a spleen item that doesn't use 'chew')" );
					}
					if ( full > 0 &&
						KoLCharacter.getSpleenUse() + full > KoLCharacter.getSpleenLimit() )
					{
						cmd = "";
					}
					if ( !ConsumablesDatabase.meetsLevelRequirement( iname ) )
					{
						if ( includeAll )
						{
							text = "level up & " + text;
							cmd = "";
						}
						else continue;
					}

					if ( cmd.length() > 0 )
					{
						Concoction c = ConcoctionPool.get( item );
						price = c.price;
						itemsCreatable = c.creatable;
						int count = Math.max( 0, item.getCount() - c.initial );
						if ( count > 0 )
						{
							int create = Math.min( count, c.creatable );
							count -= create;
							if ( create > 0 )
							{
								text = create > 1 ? "make " + create + " & " + text
									: "make & " + text;
							}
							int buy = price > 0 ? Math.min( count, KoLCharacter.getAvailableMeat() / price ) : 0;
							count -= buy;
							if ( buy > 0 && InventoryManager.canUseNPCStores( item ) )
							{
								text = buy > 1 ? "buy " + buy + " & " + text
									: "buy & " + text;
								cmd = "buy " + buy + " \u00B6" + item.getItemId() +
									";" + cmd;
							}
							if ( count > 0 )
							{
								if ( !InventoryManager.canUseMall( item ) )
								{
									continue;
								}
								text = count > 1 ? "acquire " + count + " & " + text
									: "acquire & " + text;
							}
						}
						if ( priceLevel == 2 || (priceLevel == 1 && count > 0) )
						{
							if ( price <= 0 && InventoryManager.canUseMall ( item ) )
							{
								if ( MallPriceDatabase.getPrice( item.getItemId() )
									> maxPrice * 2 )
								{
									continue;
								}

								// Depending on preference, either get historical mall price or look it up
								if ( Preferences.getBoolean( "maximizerCurrentMallPrices" ) )
								{
									price = StoreManager.getMallPrice( item );
								}
								else
								{
									price = StoreManager.getMallPrice( item, 7.0f );
								}
							}
						}
						if ( price > maxPrice || price == -1 ) continue;
					}
					else if ( item.getCount( KoLConstants.inventory ) == 0 )
					{
						continue;
					}
					itemsRemaining = item.getCount( KoLConstants.inventory );
				}

				text = text + " (";
				if ( advCost > 0 )
				{
					text += advCost + " adv, ";
					if ( advCost > KoLCharacter.getAdventuresLeft() )
					{
						cmd = "";
					}
				}
				if ( mpCost > 0 )
				{
					text += mpCost + " mp, ";
					// Don't ever grey out as we can recover MP
				}
				if ( soulsauceCost > 0 )
				{
					text += soulsauceCost + " soulsauce, ";
					if ( soulsauceCost > KoLCharacter.getSoulsauce() )
					{
						cmd = "";
					}
				}
				if ( thunderCost > 0 )
				{
					text += thunderCost + " dB of thunder, ";
					if ( thunderCost > KoLCharacter.getThunder() )
					{
						cmd = "";
					}
				}
				else if ( rainCost > 0 )
				{
					text += rainCost + " drops of rain, ";
					if ( rainCost > KoLCharacter.getRain() )
					{
						cmd = "";
					}
				}
				else if ( lightningCost > 0 )
				{
					text += lightningCost + " bolts of lightning, ";
					if ( lightningCost > KoLCharacter.getLightning() )
					{
						cmd = "";
					}
				}
				if ( price > 0 )
				{
					text += KoLConstants.COMMA_FORMAT.format( price ) + " meat, ";
					if ( price > KoLCharacter.getAvailableMeat() )
					{
						cmd = "";
					}
				}
				text += KoLConstants.MODIFIER_FORMAT.format( delta ) + ")";
				if ( Preferences.getBoolean( "verboseMaximizer" ) )
				{
					boolean show = duration > 0 ||
									( usesRemaining > 0 && usesRemaining < Integer.MAX_VALUE ) ||
									itemsRemaining + itemsCreatable > 0;
					int count = 0;
					if ( show )
					{
						text += " [";
					}
					if ( duration > 0 )
					{
						if ( duration == 999 )
						{
							text += "intrinsic";
						}
						else if ( duration == 1 )
						{
							text += "1 adv duration";
						}
						else
						{
							text += duration + " advs duration";
						}
						count++;
					}
					if ( usesRemaining > 0 && usesRemaining < Integer.MAX_VALUE )
					{
						if ( count > 0 )
						{
							text += ", ";
						}
						if ( usesRemaining == 1 )
						{
							text += "1 use remaining";
							count++;
						}
						else
						{
							text += usesRemaining + " uses remaining";
							count++;
						}
					}
					if ( itemsRemaining > 0 )
					{
						if ( count > 0 )
						{
							text += ", ";
						}
						text += itemsRemaining + " in inventory";
						count++;
					}
					if ( itemsCreatable > 0 )
					{
						if ( count > 0 )
						{
							text += ", ";
						}
						text += itemsCreatable + " creatable";
						count++;
					}
					if ( show )
					{
						text += "]";
					}
				}
				if ( orFlag )
				{
					text = "...or " + text;
				}
				Maximizer.boosts.add( new Boost( cmd, text, effect, hasEffect,
					item, delta, isSpecial ) );
				orFlag = true;
			}
		}

		if ( Maximizer.boosts.size() == 0 )
		{
			Maximizer.boosts.add( new Boost( "", "(nothing useful found)", 0, null, 0.0 ) );
		}

		Maximizer.boosts.sort();
	}

	private static int emitSlot( int slot, int equipLevel, int maxPrice, int priceLevel, double current )
	{
		if ( slot == EquipmentManager.FAMILIAR )
		{	// Insert any familiar switch at this point
			FamiliarData fam = Maximizer.best.getFamiliar();
			if ( !fam.equals( KoLCharacter.getFamiliar() ) )
			{
				MaximizerSpeculation spec = new MaximizerSpeculation();
				spec.setFamiliar( fam );
				double delta = spec.getScore() - current;
				String cmd, text;
				cmd = "familiar " + fam.getRace();
				text = cmd + " (" +
					KoLConstants.MODIFIER_FORMAT.format( delta ) + ")";

				Boost boost = new Boost( cmd, text, fam, delta );
				if ( equipLevel == -1 )
				{	// called from CLI
					boost.execute( true );
					if ( !KoLmafia.permitsContinue() ) equipLevel = 1;
				}
				else
				{
					Maximizer.boosts.add( boost );
				}
			}
		}

		String slotname = EquipmentRequest.slotNames[ slot ];
		AdventureResult item = Maximizer.best.equipment[ slot ];
		int itemId = -1;
		FamiliarData enthroned = Maximizer.best.getEnthroned();
		FamiliarData bjorned = Maximizer.best.getBjorned();
		String edPiece = Maximizer.best.getEdPiece();
		String snowsuit = Maximizer.best.getSnowsuit();
		AdventureResult curr = EquipmentManager.getEquipment( slot );
		FamiliarData currEnthroned = KoLCharacter.getEnthroned();
		FamiliarData currBjorned = KoLCharacter.getBjorned();
		String currEdPiece = Preferences.getString( "edPiece" );
		Boolean setEdPiece = false;
		String currSnowsuit = Preferences.getString( "snowsuit" );
		Boolean setSnowsuit = false;
		
		if ( item == null )
		{
			item = EquipmentRequest.UNEQUIP;
		}
		else
		{
			itemId = item.getItemId();
		}
		if ( curr.equals( item ) &&
			!( itemId == ItemPool.HATSEAT && enthroned != currEnthroned ) &&
			!( itemId == ItemPool.BUDDY_BJORN && bjorned != currBjorned ) &&
			!( itemId == ItemPool.CROWN_OF_ED && edPiece != null && !edPiece.equals( currEdPiece ) ) &&
			!( itemId == ItemPool.SNOW_SUIT && snowsuit != null && !snowsuit.equals( currSnowsuit ) ) )
		{
			if ( slot >= EquipmentManager.SLOTS ||
			     curr.equals( EquipmentRequest.UNEQUIP ) ||
			     equipLevel == -1 )
			{
				return equipLevel;
			}
			Maximizer.boosts.add( new Boost( "", "keep " + slotname + ": " + item.getName(), -1, item, 0.0 ) );
			return equipLevel;
		}
		MaximizerSpeculation spec = new MaximizerSpeculation();
		spec.equip( slot, item );
		if ( itemId == ItemPool.HATSEAT )
		{
			spec.setEnthroned( enthroned );
		}
		else if ( itemId == ItemPool.BUDDY_BJORN )
		{
			spec.setBjorned( bjorned );
		}
		else if ( itemId == ItemPool.CROWN_OF_ED )
		{
			spec.setEdPiece( edPiece );
		}
		else if ( itemId == ItemPool.SNOW_SUIT )
		{
			spec.setSnowsuit( snowsuit );
		}
		double delta = spec.getScore() - current;
		String cmd, text;
		if ( item.equals( EquipmentRequest.UNEQUIP ) )
		{
			item = curr;
			cmd = "unequip " + slotname;
			text = cmd + " (" + curr.getName() + ", " +
				KoLConstants.MODIFIER_FORMAT.format( delta ) + ")";
		}
		else
		{
			if ( itemId == ItemPool.HATSEAT && enthroned != currEnthroned )
			{
				cmd = "enthrone " + enthroned.getRace();
				text = cmd;
			}
			else if ( itemId == ItemPool.BUDDY_BJORN && bjorned != currBjorned )
			{
				cmd = "bjornify " + bjorned.getRace();
				text = cmd;
			}
			else if ( itemId == ItemPool.CROWN_OF_ED && edPiece != currEdPiece )
			{
				cmd = "edpiece " + edPiece;
				text = cmd;
				setEdPiece = true;
			}
			else if ( itemId == ItemPool.SNOW_SUIT && snowsuit != currSnowsuit )
			{
				cmd = "snowsuit " + snowsuit;
				text = cmd;
				setSnowsuit = true;
			}
			else
			{
				cmd = "equip " + slotname + " \u00B6" + item.getItemId();
				text = "equip " + slotname + " " + item.getName();
			}
			text = text + " (";

			CheckedItem checkedItem = new CheckedItem( itemId, equipLevel, maxPrice, priceLevel );

			int price = 0;

			// How many have been needed so far to make this maximization set?
			// We need 1 + that number to equip this item, not just 1
			int count = 0;

			// If we're running from command line then execute them straight away,
			// so we have to count how much we've used in 'earlier' items
			if ( equipLevel == -1 )
			{
				for ( int piece = EquipmentManager.HAT ; piece < slot ; piece++ )
				{
					AdventureResult equipped = EquipmentManager.getEquipment( piece );
					if ( equipped != null && item.getItemId() == equipped.getItemId() )
					{
						count++;
					}
				}
			}
			else
			// Otherwise we iterate through the maximization set so far
			{
				Iterator i = Maximizer.boosts.iterator();
				while ( i.hasNext() )
				{
					Object boost = i.next();
					if ( boost instanceof Boost )
					{
						if ( item.equals( ((Boost) boost).getItem() ) )
						{
							count++;
						}
					}
				}
			}

			// The "initial" quantity comes from InventoryManager.getAccessibleCount.
			// It can include inventory, closet, and storage.  However, anything that
			// is included should also be supported by retrieveItem(), so we don't need
			// to take any special action here.  Displaying the method that will be used
			// would still be useful, though.
			if ( curr.equals( item ) )
			{
			}
			else if ( checkedItem.initial > count )
			{
				// This may look odd, but we need an item, not a checked item
				// The count of a checked item includes creatable, buyable, pullable etc.
				String method = InventoryManager.simRetrieveItem( ItemPool.get( item.getItemId(), count + 1 ),
					equipLevel == -1, false );
				if ( !method.equals( "have" ) )
				{
					text = method + " & " + text;
				}
				if ( method.equals( "uncloset" ) )
				{
					cmd = "closet take 1 \u00B6" + item.getItemId() + ";" + cmd;
				}
				else if ( method.equals( "unstash" ) )
				{
					cmd = "stash take 1 \u00B6" + item.getItemId() + ";" + cmd;
				}
				// Should be only hitting this after Ronin I think
				else if ( method.equals( "pull" ) ) 
				{
					cmd = "pull 1 \u00B6" + item.getItemId() + ";" + cmd;
				}
			}
			else if ( checkedItem.creatable + checkedItem.initial > count )
			{
				text = "make & " + text;
				cmd = "make \u00B6" + item.getItemId() + ";" + cmd;
			}
			else if ( checkedItem.npcBuyable + checkedItem.initial > count )
			{
				text = "buy & " + text;
				cmd = "buy 1 \u00B6" + item.getItemId() + ";" + cmd;
				price = ConcoctionPool.get( item ).price;
			}
			else if ( checkedItem.foldable + checkedItem.initial > count )
			{
				// We assume that there is only one available fold item type of the right group.
				// Not always right, but will do for now.
				String method = InventoryManager.simRetrieveItem( ItemPool.get( checkedItem.foldItemId, count + 1 ) );
				if ( method.equals( "have" ) || method.equals( "remove" ) )
				{
					text = "fold & " + text;
					cmd = "fold \u00B6" + item.getItemId() + ";" + cmd;
				}
				else
				{
					text = method + " & fold & " + text;
					cmd = "acquire 1 \u00B6" + checkedItem.foldItemId + ";fold \u00B6" + item.getItemId() + ";" + cmd;
				}
			}
			else if ( checkedItem.pullable + checkedItem.initial > count )
			{
				text = "pull & " + text;
				cmd = "pull \u00B6" + item.getItemId() + ";" + cmd;
			}
			else if ( checkedItem.pullfoldable + checkedItem.initial > count )
			{
				// We assume that there is only one available fold item type of the right group.
				// Not always right, but will do for now.
				text = "pull & fold & " + text;
				cmd = "pull 1 \u00B6" + checkedItem.foldItemId + ";fold \u00B6" + item.getItemId() + ";" + cmd;
			}
			else 	// Mall buyable
			{
				text = "acquire & " + text;
				if ( priceLevel > 0 )
				{
					price = StoreManager.getMallPrice( item );
				}
			}

			if ( price > 0 )
			{
				text = text + KoLConstants.COMMA_FORMAT.format( price ) +
					" meat, ";
			}
			text = text + KoLConstants.MODIFIER_FORMAT.format(
				delta ) + ")";
		}

		if ( !setEdPiece )
		{
			edPiece = null;
		}

		if ( !setSnowsuit )
		{
			snowsuit = null;
		}

		Boost boost = new Boost( cmd, text, slot, item, delta, enthroned, bjorned, edPiece, snowsuit );
		if ( equipLevel == -1 )
		{	// called from CLI
			boost.execute( true );
			if ( !KoLmafia.permitsContinue() )
			{
				equipLevel = 1;
				Maximizer.boosts.add( boost );
			}
		}
		else
		{
			Maximizer.boosts.add( boost );
		}
		return equipLevel;
	}
}
