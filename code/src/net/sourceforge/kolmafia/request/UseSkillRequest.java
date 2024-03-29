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

import java.util.HashMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.BuffBotHome;
import net.sourceforge.kolmafia.KoLAdventure;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.Modifiers;
import net.sourceforge.kolmafia.PastaThrallData;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.RequestThread;
import net.sourceforge.kolmafia.SpecialOutfit;
import net.sourceforge.kolmafia.Speculation;

import net.sourceforge.kolmafia.moods.HPRestoreItemList;
import net.sourceforge.kolmafia.moods.MoodManager;
import net.sourceforge.kolmafia.moods.RecoveryManager;

import net.sourceforge.kolmafia.objectpool.Concoction;
import net.sourceforge.kolmafia.objectpool.EffectPool;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.objectpool.SkillPool;

import net.sourceforge.kolmafia.persistence.ConcoctionDatabase;
import net.sourceforge.kolmafia.persistence.SkillDatabase;

import net.sourceforge.kolmafia.preferences.Preferences;
import net.sourceforge.kolmafia.request.CharPaneRequest.Companion;

import net.sourceforge.kolmafia.session.ChoiceManager;
import net.sourceforge.kolmafia.session.ContactManager;
import net.sourceforge.kolmafia.session.ConsequenceManager;
import net.sourceforge.kolmafia.session.DreadScrollManager;
import net.sourceforge.kolmafia.session.EquipmentManager;
import net.sourceforge.kolmafia.session.InventoryManager;
import net.sourceforge.kolmafia.session.ResultProcessor;

import net.sourceforge.kolmafia.utilities.InputFieldUtilities;
import net.sourceforge.kolmafia.utilities.LockableListFactory;
import net.sourceforge.kolmafia.utilities.StringUtilities;

public class UseSkillRequest
	extends GenericRequest
	implements Comparable<UseSkillRequest>
{
	private static final HashMap<String, UseSkillRequest> ALL_SKILLS = new HashMap<String, UseSkillRequest>();
	private static final Pattern SKILLID_PATTERN = Pattern.compile( "whichskill=(\\d+)" );
	private static final Pattern BOOKID_PATTERN = Pattern.compile( "preaction=(?:summon|combine)([^&]*)" );

	private static final Pattern COUNT_PATTERN = Pattern.compile( "quantity=([\\*\\d,]+)" );

	// <p>1 / 50 casts used today.</td>
	private static final Pattern LIMITED_PATTERN = Pattern.compile( "<p>(\\d+) / [\\d]+ casts used today\\.</td>", Pattern.DOTALL );

	private static final Pattern SKILLZ_PATTERN = Pattern.compile( "rel=\\\"(\\d+)\\\".*?<span class=small>(.*?)</font></center></span>", Pattern.DOTALL );

	public static final String[] BREAKFAST_SKILLS =
	{
		"Advanced Cocktailcrafting",
		"Advanced Saucecrafting",
		"Pastamastery",
		"Summon Crimbo Candy",
		"Lunch Break",
		"Spaghetti Breakfast",
		"Grab a Cold One",
		"Summon Holiday Fun!",
		"Summon Carrot",
		"Summon Kokomo Resort Pass",
		"Perfect Freeze",
	};

	// These are skills where someone would not care if they are in-run,
	// generally because they do not cost MP
	public static final String[] BREAKFAST_ALWAYS_SKILLS =
	{
		"Summon Annoyance",
		"Communism!",
	};

	public static final String[] TOME_SKILLS =
	{
		"Summon Snowcones",
		"Summon Stickers",
		"Summon Sugar Sheets",
		// Summon Clip Art requires extra parameters
		// "Summon Clip Art",
		"Summon Rad Libs",
		"Summon Smithsness"
	};

	public static final String[] LIBRAM_SKILLS =
	{
		"Summon Candy Heart",
		"Summon Party Favor",
		"Summon Love Song",
		"Summon BRICKOs",
		"Summon Dice",
		"Summon Resolutions",
		"Summon Taffy",
	};

	public static final String[] GRIMOIRE_SKILLS =
	{
		"Summon Hilarious Objects",
		"Summon Tasteful Items",
		"Summon Alice's Army Cards",
		"Summon Geeky Gifts",
		"Summon Confiscated Things",
	};

	public static String lastUpdate = "";
	public static int lastSkillUsed = -1;
	public static int lastSkillCount = 0;

	private final int skillId;
	private final boolean isBuff;
	private final String skillName;
	private String target;
	private int buffCount;
	private String countFieldId;
	private boolean isRunning;

	private int lastReduction = Integer.MAX_VALUE;
	private String lastStringForm = "";

	// Tools for casting buffs. The lists are ordered from most bonus buff
	// turns provided by this tool to least.

	public static final BuffTool[] TAMER_TOOLS = new BuffTool[]
	{
		new BuffTool( ItemPool.FLAIL_OF_THE_SEVEN_ASPECTS, 15, false, null ),
		new BuffTool( ItemPool.CHELONIAN_MORNINGSTAR, 10, false, null ),
		new BuffTool( ItemPool.MACE_OF_THE_TORTOISE, 5, false, null ),
		new BuffTool( ItemPool.OUIJA_BOARD, 2, true, null ),
		new BuffTool( ItemPool.TURTLE_TOTEM, 0, false , null ),
	};

	public static final BuffTool[] SAUCE_TOOLS = new BuffTool[]
	{
		new BuffTool( ItemPool.WINDSOR_PAN_OF_THE_SOURCE, 15, false, null ),
		new BuffTool( ItemPool.FRYING_BRAINPAN, 15, false, null ),
		new BuffTool( ItemPool.SAUCEPANIC, 10, false, null ),
		new BuffTool( ItemPool.SEVENTEEN_ALARM_SAUCEPAN, 10, false, null ),
		new BuffTool( ItemPool.OIL_PAN, 7, true, null ),
		new BuffTool( ItemPool.FIVE_ALARM_SAUCEPAN, 5, false, null ),
		new BuffTool( ItemPool.SAUCEPAN, 0, false, null ),
	};

	public static final BuffTool[] THIEF_TOOLS = new BuffTool[]
	{
		new BuffTool( ItemPool.TRICKSTER_TRIKITIXA, 15, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.ZOMBIE_ACCORDION, 15, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.ALARM_ACCORDION, 15, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.PEACE_ACCORDION, 14, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.ACCORDIONOID_ROCCA, 13, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.PYGMY_CONCERTINETTE, 12, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.GHOST_ACCORDION, 11, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.SQUEEZEBOX_OF_THE_AGES, 10, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.SHAKESPEARES_SISTERS_ACCORDION, 10, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.AUTOCALLIOPE, 10, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.NON_EUCLIDEAN_NON_ACCORDION, 10, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.ACCORDION_OF_JORDION, 9, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.PENTATONIC_ACCORDION, 7, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.BONE_BANDONEON, 6, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.ANTIQUE_ACCORDION, 5, true, null ),
		new BuffTool( ItemPool.ACCORD_ION, 5, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.ACCORDION_FILE, 5, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.BAL_MUSETTE_ACCORDION, 5, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.CAJUN_ACCORDION, 5, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.QUIRKY_ACCORDION, 5, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.SKIPPERS_ACCORDION, 5, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.ROCK_N_ROLL_LEGEND, 5, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.GUANCERTINA, 4, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.MAMAS_SQUEEZEBOX, 3, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.BARITONE_ACCORDION, 2, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.CALAVERA_CONCERTINA, 2, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.BEER_BATTERED_ACCORDION, 1, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.STOLEN_ACCORDION, 0, false, KoLCharacter.ACCORDION_THIEF ),
		new BuffTool( ItemPool.TOY_ACCORDION, 0, false, null ),
		new BuffTool( ItemPool.AEROGEL_ACCORDION, 5, false, null ),
	};

	public static final AdventureResult WIZARD_HAT = ItemPool.get( ItemPool.JEWEL_EYED_WIZARD_HAT, 1 );

	public static final AdventureResult SHAKESPEARES_SISTERS_ACCORDION = ItemPool.get( ItemPool.SHAKESPEARES_SISTERS_ACCORDION, 1 );
	public static final AdventureResult BASS_CLARINET = ItemPool.get( ItemPool.BASS_CLARINET, 1 );

	public static final AdventureResult PLEXI_WATCH = ItemPool.get( ItemPool.PLEXIGLASS_POCKETWATCH, 1 );
	public static final AdventureResult BRIM_BRACELET = ItemPool.get( ItemPool.BRIMSTONE_BRACELET, 1 );
	public static final AdventureResult POCKET_SQUARE = ItemPool.get( ItemPool.POCKET_SQUARE, 1 );
	public static final AdventureResult SOLITAIRE = ItemPool.get( ItemPool.STAINLESS_STEEL_SOLITAIRE, 1 );

	public static final AdventureResult NAVEL_RING = ItemPool.get( ItemPool.NAVEL_RING, 1 );
	public static final AdventureResult WIRE_BRACELET = ItemPool.get( ItemPool.WOVEN_BALING_WIRE_BRACELETS, 1 );
	public static final AdventureResult BACON_BRACELET = ItemPool.get( ItemPool.BACONSTONE_BRACELET, 1 );
	public static final AdventureResult BACON_EARRING = ItemPool.get( ItemPool.BACONSTONE_EARRING, 1 );
	public static final AdventureResult SOLID_EARRING = ItemPool.get( ItemPool.SOLID_BACONSTONE_EARRING, 1 );
	public static final AdventureResult EMBLEM_AKGYXOTH = ItemPool.get( ItemPool.EMBLEM_AKGYXOTH, 1 );

	public static final AdventureResult SAUCEBLOB_BELT = ItemPool.get( ItemPool.SAUCEBLOB_BELT, 1 );
	public static final AdventureResult JUJU_MOJO_MASK = ItemPool.get( ItemPool.JUJU_MOJO_MASK, 1 );

	private static final AdventureResult[] AVOID_REMOVAL = new AdventureResult[]
	{
		UseSkillRequest.BASS_CLARINET,	// -3
		UseSkillRequest.BRIM_BRACELET,	// -3
		UseSkillRequest.PLEXI_WATCH,	// -3
		UseSkillRequest.POCKET_SQUARE,	// -3
		UseSkillRequest.SOLITAIRE,		// -2
		UseSkillRequest.SHAKESPEARES_SISTERS_ACCORDION,		// -1 or -2
		UseSkillRequest.WIZARD_HAT,		// -1
		UseSkillRequest.NAVEL_RING,		// -1
		UseSkillRequest.WIRE_BRACELET,	// -1
		UseSkillRequest.BACON_BRACELET,	// -1, discontinued item
		UseSkillRequest.BACON_EARRING,	// -1
		UseSkillRequest.SOLID_EARRING,	// -1
		UseSkillRequest.EMBLEM_AKGYXOTH,	// -1
		// Removing the following may lose a buff
		UseSkillRequest.JUJU_MOJO_MASK,
	};

	// The number of items at the end of AVOID_REMOVAL that are simply
	// there to avoid removal - there's no point in equipping them
	// temporarily during casting:

	private static final int AVOID_REMOVAL_ONLY = 1;

	// Other known MP cost items:
	// Vile Vagrant Vestments (-5) - unlikely to be equippable during Ronin.
	// Idol of Ak'gyxoth (-1) - off-hand, would require special handling.

	private UseSkillRequest( final String skillName )
	{
		super( UseSkillRequest.chooseURL( skillName ) );

		this.skillId = SkillDatabase.getSkillId( skillName );
		if ( this.skillId == -1 )
		{
			RequestLogger.printLine( "Unrecognized skill: " + skillName );
			this.skillName = skillName;
			this.isBuff = false;
		}
		else
		{
			this.skillName = SkillDatabase.getSkillName( this.skillId );
			this.isBuff = SkillDatabase.isBuff( this.skillId );
		}

		this.target = null;
		this.addFormFields();
	}

	private static String chooseURL( final String skillName )
	{
		if ( SkillDatabase.isBookshelfSkill( skillName ) )
		{
			return "campground.php";
		}

		return "runskillz.php";
	}

	private void addFormFields()
	{
		switch ( this.skillId )
		{
		case SkillPool.SNOWCONE:
			this.addFormField( "preaction", "summonsnowcone" );
			break;

		case SkillPool.STICKER:
			this.addFormField( "preaction", "summonstickers" );
			break;

		case SkillPool.SUGAR:
			this.addFormField( "preaction", "summonsugarsheets" );
			break;

		case SkillPool.CLIP_ART:
			this.addFormField( "preaction", "combinecliparts" );
			break;

		case SkillPool.RAD_LIB:
			this.addFormField( "preaction", "summonradlibs" );
			break;

		case SkillPool.SMITHSNESS:
			this.addFormField( "preaction", "summonsmithsness" );
			break;

		case SkillPool.HILARIOUS:
			this.addFormField( "preaction", "summonhilariousitems" );
			break;

		case SkillPool.TASTEFUL:
			this.addFormField( "preaction", "summonspencersitems" );
			break;

		case SkillPool.CARDS:
			this.addFormField( "preaction", "summonaa" );
			break;

		case SkillPool.GEEKY:
			this.addFormField( "preaction", "summonthinknerd" );
			break;

		case SkillPool.CANDY_HEART:
			this.addFormField( "preaction", "summoncandyheart" );
			break;

		case SkillPool.PARTY_FAVOR:
			this.addFormField( "preaction", "summonpartyfavor" );
			break;

		case SkillPool.LOVE_SONG:
			this.addFormField( "preaction", "summonlovesongs" );
			break;

		case SkillPool.BRICKOS:
			this.addFormField( "preaction", "summonbrickos" );
			break;

		case SkillPool.DICE:
			this.addFormField( "preaction", "summongygax" );
			break;

		case SkillPool.RESOLUTIONS:
			this.addFormField( "preaction", "summonresolutions" );
			break;

		case SkillPool.TAFFY:
			this.addFormField( "preaction", "summontaffy" );
			break;

		case SkillPool.CONFISCATOR:
			this.addFormField( "preaction", "summonconfiscators" );
			break;

		default:
			this.addFormField( "action", "Skillz" );
			this.addFormField( "whichskill", String.valueOf( this.skillId ) );
			this.addFormField( "ajax", "1" );
			break;
		}
	}

	public void setTarget( final String target )
	{
		this.countFieldId = "quantity";
		if ( this.isBuff )
		{
			if ( target == null || target.trim().length() == 0 || target.equals( KoLCharacter.getPlayerId() ) || target.equals( KoLCharacter.getUserName() ) )
			{
				this.target = null;
				this.addFormField( "targetplayer", KoLCharacter.getPlayerId() );
			}
			else
			{
				this.target = ContactManager.getPlayerName( target );
				this.addFormField( "targetplayer", ContactManager.getPlayerId( target ) );
			}
		}
		else
		{
			this.target = null;
		}
	}

	public void setBuffCount( int buffCount )
	{
		int maxPossible = 0;

		if ( SkillDatabase.isSoulsauceSkill( skillId ) )
		{
			maxPossible = KoLCharacter.getSoulsauce() / SkillDatabase.getSoulsauceCost( skillId );
		}
		else if ( SkillDatabase.isThunderSkill( skillId ) )
		{
			maxPossible = KoLCharacter.getThunder() / SkillDatabase.getThunderCost( skillId );
		}
		else if ( SkillDatabase.isRainSkill( skillId ) )
		{
			maxPossible = KoLCharacter.getRain() / SkillDatabase.getRainCost( skillId );
		}
		else if ( SkillDatabase.isLightningSkill( skillId ) )
		{
			maxPossible = KoLCharacter.getLightning() / SkillDatabase.getLightningCost( skillId );
		}
		else
		{
			int mpCost = SkillDatabase.getMPConsumptionById( this.skillId );
			int availableMP = KoLCharacter.getCurrentMP();
			if ( mpCost == 0 )
			{
				maxPossible = this.getMaximumCast();
			}
			else if ( SkillDatabase.isLibramSkill( this.skillId ) )
			{
				maxPossible = SkillDatabase.libramSkillCasts( availableMP );
			}
			else
			{
				maxPossible = Math.min( this.getMaximumCast(), availableMP / mpCost );
			}
		}

		if ( buffCount < 1 )
		{
			buffCount += maxPossible;
		}
		else if ( buffCount == Integer.MAX_VALUE )
		{
			buffCount = maxPossible;
		}

		this.buffCount = buffCount;
	}

	public int compareTo( final UseSkillRequest o )
	{
		if ( o == null || !( o instanceof UseSkillRequest ) )
		{
			return -1;
		}

		int mpDifference =
			SkillDatabase.getMPConsumptionById( this.skillId ) - SkillDatabase.getMPConsumptionById( ( (UseSkillRequest) o ).skillId );

		return mpDifference != 0 ? mpDifference : this.skillName.compareToIgnoreCase( ( (UseSkillRequest) o ).skillName );
	}

	public int getSkillId()
	{
		return this.skillId;
	}

	public String getSkillName()
	{
		return this.skillName;
	}

	public int getMaximumCast()
	{
		int maximumCast = Integer.MAX_VALUE;

		boolean canCastHoboSong =
			KoLCharacter.getClassType() == KoLCharacter.ACCORDION_THIEF && KoLCharacter.getLevel() > 14;

		switch ( this.skillId )
		{
		// The Smile of Mr. A can be used five times per day per Golden
		// Mr. Accessory you own
		case SkillPool.SMILE_OF_MR_A:
			maximumCast =
				Preferences.getInteger( "goldenMrAccessories" ) * 5 -
				Preferences.getInteger( "_smilesOfMrA" );
			break;

		// Vent Rage Gland can be used once per day
		case SkillPool.RAGE_GLAND:
			maximumCast = Preferences.getBoolean( "rageGlandVented" ) ? 0 : 1;
			break;

		// You can take a Lunch Break once a day
		case SkillPool.LUNCH_BREAK:
			maximumCast = Preferences.getBoolean( "_lunchBreak" ) ? 0 : 1;
			break;

		// Spaghetti Breakfast once a day
		case SkillPool.SPAGHETTI_BREAKFAST:
			maximumCast = Preferences.getBoolean( "_spaghettiBreakfast" ) ? 0 : 1;
			break;

		// Grab a Cold One once a day
		case SkillPool.GRAB_A_COLD_ONE:
			maximumCast = Preferences.getBoolean( "_coldOne" ) ? 0 : 1;
			break;

		// That's Not a Knife once a day
		case SkillPool.THATS_NOT_A_KNIFE:
			maximumCast = Preferences.getBoolean( "_discoKnife" ) ? 0 : 1;
			break;

		// Summon "Boner Battalion" can be used once per day
		case SkillPool.SUMMON_BONERS:
			maximumCast = Preferences.getBoolean( "_bonersSummoned" ) ? 0 : 1;
			break;

		case SkillPool.REQUEST_SANDWICH:
			maximumCast = Preferences.getBoolean( "_requestSandwichSucceeded" ) ? 0 : 1;
			break;

		// Tomes can be used three times per day.  In aftercore, each tome can be used 3 times per day.

		case SkillPool.SNOWCONE:
			maximumCast = KoLCharacter.canInteract() ? Math.max( 3 - Preferences.getInteger( "_snowconeSummons" ), 0 ) :
				Math.max( 3 - Preferences.getInteger( "tomeSummons" ), 0 );
			break;

		case SkillPool.STICKER:
			maximumCast = KoLCharacter.canInteract() ? Math.max( 3 - Preferences.getInteger( "_stickerSummons" ), 0 ) :
				Math.max( 3 - Preferences.getInteger( "tomeSummons" ), 0 );
			break;

		case SkillPool.SUGAR:
			maximumCast = KoLCharacter.canInteract() ? Math.max( 3 - Preferences.getInteger( "_sugarSummons" ), 0 ) :
				Math.max( 3 - Preferences.getInteger( "tomeSummons" ), 0 );
			break;

		case SkillPool.CLIP_ART:
			maximumCast = KoLCharacter.canInteract() ? Math.max( 3 - Preferences.getInteger( "_clipartSummons" ), 0 ) :
				Math.max( 3 - Preferences.getInteger( "tomeSummons" ), 0 );
			break;

		case SkillPool.RAD_LIB:
			maximumCast = KoLCharacter.canInteract() ? Math.max( 3 - Preferences.getInteger( "_radlibSummons" ), 0 ) :
				Math.max( 3 - Preferences.getInteger( "tomeSummons" ), 0 );
			break;

		case SkillPool.SMITHSNESS:
			maximumCast = KoLCharacter.canInteract() ? Math.max( 3 - Preferences.getInteger( "_smithsnessSummons" ), 0 ) :
				Math.max( 3 - Preferences.getInteger( "tomeSummons" ), 0 );
			break;

		// Grimoire items can only be summoned once per day.
		case SkillPool.HILARIOUS:
			maximumCast = Math.max( 1 - Preferences.getInteger( "grimoire1Summons" ), 0 );
			break;

		case SkillPool.TASTEFUL:
			maximumCast = Math.max( 1 - Preferences.getInteger( "grimoire2Summons" ), 0 );
			break;

		case SkillPool.CARDS:
			maximumCast = Math.max( 1 - Preferences.getInteger( "grimoire3Summons" ), 0 );
			break;

		case SkillPool.GEEKY:
			maximumCast = Math.max( 1 - Preferences.getInteger( "_grimoireGeekySummons" ), 0 );
			break;

		case SkillPool.CONFISCATOR:
			maximumCast = Math.max( 1 - Preferences.getInteger( "_grimoireConfiscatorSummons" ), 0 );
			break;

		// You can summon Crimbo candy once a day
		case SkillPool.CRIMBO_CANDY:
			maximumCast = Math.max( 1 - Preferences.getInteger( "_candySummons" ), 0 );
			break;

		// Rainbow Gravitation can be cast 3 times per day.  Each
		// casting consumes five elemental wads and a twinkly wad

		case SkillPool.RAINBOW_GRAVITATION:
			maximumCast = Math.max( 3 - Preferences.getInteger( "prismaticSummons" ), 0 );
			maximumCast = Math.min( InventoryManager.getAccessibleCount( ItemPool.COLD_WAD ), maximumCast );
			maximumCast = Math.min( InventoryManager.getAccessibleCount( ItemPool.HOT_WAD ), maximumCast );
			maximumCast = Math.min( InventoryManager.getAccessibleCount( ItemPool.SLEAZE_WAD ), maximumCast );
			maximumCast = Math.min( InventoryManager.getAccessibleCount( ItemPool.SPOOKY_WAD ), maximumCast );
			maximumCast = Math.min( InventoryManager.getAccessibleCount( ItemPool.STENCH_WAD ), maximumCast );
			maximumCast = Math.min( InventoryManager.getAccessibleCount( ItemPool.TWINKLY_WAD ), maximumCast );
			break;

		case SkillPool.PASTAMASTERY:
			maximumCast = ( Preferences.getInteger( "noodleSummons" ) == 0 ) ? 1 : 0;
			break;

		// Canticle of Carboloading can be cast once per day.
		case SkillPool.CARBOLOADING:
			maximumCast = Preferences.getBoolean( "_carboLoaded" ) ? 0 : 1;
			break;

		case SkillPool.ADVANCED_SAUCECRAFTING:
			maximumCast = ( Preferences.getInteger( "reagentSummons" ) == 0 ) ? 1 : 0;
			break;

		case SkillPool.ADVANCED_COCKTAIL:
			maximumCast = ( Preferences.getInteger( "cocktailSummons" ) == 0 ) ? 1 : 0;
			break;

		case SkillPool.THINGFINDER:
			maximumCast = canCastHoboSong ? Math.max( 10 - Preferences.getInteger( "_thingfinderCasts" ), 0 ) : 0;
			break;

		case SkillPool.BENETTONS:
			maximumCast = canCastHoboSong ? Math.max( 10 - Preferences.getInteger( "_benettonsCasts" ), 0 ) : 0;
			break;

		case SkillPool.ELRONS:
			maximumCast = canCastHoboSong ? Math.max( 10 - Preferences.getInteger( "_elronsCasts" ), 0 ) : 0;
			break;

		case SkillPool.COMPANIONSHIP:
			maximumCast = canCastHoboSong ? Math.max( 10 - Preferences.getInteger( "_companionshipCasts" ), 0 ) : 0;
			break;

		case SkillPool.PRECISION:
			maximumCast = canCastHoboSong ? Math.max( 10 - Preferences.getInteger( "_precisionCasts" ), 0 ) : 0;
			break;

		case SkillPool.DONHOS:
			maximumCast = Math.max( 50 - Preferences.getInteger( "_donhosCasts" ), 0 );
			break;

		case SkillPool.INIGOS:
			maximumCast = Math.max( 5 - Preferences.getInteger( "_inigosCasts" ), 0 );
			break;

		// Avatar of Boris skill
		case SkillPool.DEMAND_SANDWICH:
			maximumCast = Math.max( 3 - Preferences.getInteger( "_demandSandwich" ), 0 );
			break;

		// Zombie Master skills
		case SkillPool.SUMMON_MINION:
			maximumCast = KoLCharacter.getAvailableMeat() / 100;
			break;

		case SkillPool.SUMMON_HORDE:
			maximumCast = KoLCharacter.getAvailableMeat() / 1000;
			break;

		// Avatar of Jarlsberg skills
		case SkillPool.CONJURE_EGGS:
			maximumCast = Preferences.getBoolean( "_jarlsEggsSummoned" ) ? 0 : 1;
			break;

		case SkillPool.CONJURE_DOUGH:
			maximumCast = Preferences.getBoolean( "_jarlsDoughSummoned" ) ? 0 : 1;
			break;

		case SkillPool.CONJURE_VEGGIES:
			maximumCast = Preferences.getBoolean( "_jarlsVeggiesSummoned" ) ? 0 : 1;
			break;

		case SkillPool.CONJURE_CHEESE:
			maximumCast = Preferences.getBoolean( "_jarlsCheeseSummoned" ) ? 0 : 1;
			break;

		case SkillPool.CONJURE_MEAT:
			maximumCast = Preferences.getBoolean( "_jarlsMeatSummoned" ) ? 0 : 1;
			break;

		case SkillPool.CONJURE_POTATO:
			maximumCast = Preferences.getBoolean( "_jarlsPotatoSummoned" ) ? 0 : 1;
			break;

		case SkillPool.CONJURE_CREAM:
			maximumCast = Preferences.getBoolean( "_jarlsCreamSummoned" ) ? 0 : 1;
			break;

		case SkillPool.CONJURE_FRUIT:
			maximumCast = Preferences.getBoolean( "_jarlsFruitSummoned" ) ? 0 : 1;
			break;

		case SkillPool.EGGMAN:
			boolean haveEgg = KoLConstants.inventory.contains( ItemPool.get( ItemPool.COSMIC_EGG, 1 ) );
			boolean eggActive = KoLCharacter.getCompanion() == Companion.EGGMAN;
			maximumCast = ( haveEgg && !eggActive ) ? 1 : 0;
			break;

		case SkillPool.RADISH_HORSE:
			boolean haveVeggie = KoLConstants.inventory.contains( ItemPool.get( ItemPool.COSMIC_VEGETABLE, 1 ) );
			boolean radishActive = KoLCharacter.getCompanion() == Companion.RADISH;
			maximumCast = ( haveVeggie && !radishActive ) ? 1 : 0;
			break;

		case SkillPool.HIPPOTATO:
			boolean havePotato = KoLConstants.inventory.contains( ItemPool.get( ItemPool.COSMIC_POTATO, 1 ) );
			boolean hippoActive = KoLCharacter.getCompanion() == Companion.HIPPO;
			maximumCast = ( havePotato && !hippoActive ) ? 1 : 0;
			break;

		case SkillPool.CREAMPUFF:
			boolean haveCream = KoLConstants.inventory.contains( ItemPool.get( ItemPool.COSMIC_CREAM, 1 ) );
			boolean creampuffActive = KoLCharacter.getCompanion() == Companion.CREAM;
			maximumCast = ( haveCream && !creampuffActive ) ? 1 : 0;
			break;

		case SkillPool.DEEP_VISIONS:
			maximumCast = KoLCharacter.getMaximumHP() >= 500 ? 1 : 0;
			break;

		case SkillPool.WAR_BLESSING:
			maximumCast = ( KoLCharacter.getBlessingLevel() != -1 ||
				KoLCharacter.getBlessingType() == KoLCharacter.WAR_BLESSING ) ? 1 : 0;
			break;
		
		case SkillPool.SHE_WHO_WAS_BLESSING:
			maximumCast = ( KoLCharacter.getBlessingLevel() != -1 ||
				KoLCharacter.getBlessingType() == KoLCharacter.SHE_WHO_WAS_BLESSING ) ? 1 : 0;
			break;
		
		case SkillPool.STORM_BLESSING:
			maximumCast = ( KoLCharacter.getBlessingLevel() != -1 ||
				KoLCharacter.getBlessingType() == KoLCharacter.STORM_BLESSING ) ? 1 : 0;
			break;
		
		case SkillPool.SPIRIT_BOON:
			maximumCast = KoLCharacter.getBlessingLevel() != 0 ? Integer.MAX_VALUE : 0;
			break;

		case SkillPool.TURTLE_POWER:
			maximumCast = KoLCharacter.getBlessingLevel() == 3 && !Preferences.getBoolean( "_turtlePowerCast" ) ? 1 : 0;
			break;

		case SkillPool.PSYCHOKINETIC_HUG:
			maximumCast = Preferences.getBoolean( "_psychokineticHugUsed" ) ? 0 : 1;
			break;

		case SkillPool.MANAGERIAL_MANIPULATION:
			maximumCast = Preferences.getBoolean( "_managerialManipulationUsed" ) ? 0 : 1;
			break;

		case SkillPool.THROW_PARTY:
			maximumCast = Preferences.getBoolean( "_petePartyThrown" ) ? 0 : 1;
			break;

		case SkillPool.INCITE_RIOT:
			maximumCast = Preferences.getBoolean( "_peteRiotIncited" ) ? 0 : 1;
			break;

		case SkillPool.SUMMON_ANNOYANCE:
			if ( Preferences.getInteger( "summonAnnoyanceCost" ) == 11 )
			{
				// If we made it this far, you should have the skill.
				// Update its cost.
				GenericRequest req = new GenericRequest(
					"desc_skill.php?whichskill=" + this.skillId + "&self=true" );
				RequestThread.postRequest( req );
			}
			if ( Preferences.getBoolean( "_summonAnnoyanceUsed" ) )
			{
				maximumCast = 0;
				break;
			}
			if ( Preferences.getInteger( "availableSwagger" ) < Preferences.getInteger( "summonAnnoyanceCost" ) )
			{
				maximumCast = 0;
				break;
			}
			maximumCast = 1;
			break;

		case SkillPool.PIRATE_BELLOW:
			maximumCast = Preferences.getBoolean( "_pirateBellowUsed" ) ? 0 : 1;
			break;

		case SkillPool.HOLIDAY_FUN:
			maximumCast = Preferences.getBoolean( "_holidayFunUsed" ) ? 0 : 1;
			break;

		case SkillPool.SUMMON_CARROT:
			maximumCast = Preferences.getBoolean( "_summonCarrotUsed" ) ? 0 : 1;
			break;

		case SkillPool.SUMMON_KOKOMO_RESORT_PASS:
			maximumCast = Preferences.getBoolean( "_summonResortPassUsed" ) ? 0 : 1;
			break;

		case SkillPool.CALCULATE_THE_UNIVERSE:
			if ( KoLCharacter.getAdventuresLeft() == 0 )
			{
				maximumCast = 0;
				break;
			}
			if ( Preferences.getInteger( "skillLevel144" ) == 0 )
			{
				// If the skill is being cast, then the limit must be at least 1
				Preferences.setInteger( "skillLevel144", 1 );
			}
			maximumCast = Preferences.getInteger( "skillLevel144" ) > Preferences.getInteger( "_universeCalculated" ) ? 1 : 0;
			break;

		case SkillPool.ANCESTRAL_RECALL:
			maximumCast = Math.min( 10 - Preferences.getInteger( "_ancestralRecallCasts" ),
			              InventoryManager.getAccessibleCount( ItemPool.BLUE_MANA ) );
			break;

		case SkillPool.DARK_RITUAL:
			maximumCast = InventoryManager.getAccessibleCount( ItemPool.BLACK_MANA );
			break;

		case SkillPool.PERFECT_FREEZE:
			maximumCast = Preferences.getBoolean( "_perfectFreezeUsed" ) ? 0 : 1;
			break;

		case SkillPool.COMMUNISM:
			maximumCast = Preferences.getBoolean( "_communismUsed" ) ? 0 : 1;
			break;

		case SkillPool.BOW_LEGGED_SWAGGER:
			maximumCast = Preferences.getBoolean( "_bowleggedSwaggerUsed" ) ? 0 : 1;
			break;

		case SkillPool.BEND_HELL:
			maximumCast = Preferences.getBoolean( "_bendHellUsed" ) ? 0 : 1;
			break;

		case SkillPool.STEELY_EYED_SQUINT:
			maximumCast = Preferences.getBoolean( "_steelyEyedSquintUsed" ) ? 0 : 1;
			break;

		case SkillPool.INTERNAL_SODA_MACHINE:
			int meatLimit = KoLCharacter.getAvailableMeat() / 20;
			int mpLimit = (int) Math.ceil( ( KoLCharacter.getMaximumMP() - KoLCharacter.getCurrentMP() ) / 10.0 );
			maximumCast = Math.min( meatLimit, mpLimit );
			break;

		case SkillPool.CECI_CHAPEAU:
			maximumCast = Preferences.getBoolean( "_ceciHatUsed" ) ? 0 : 1;
			break;

		case SkillPool.EVOKE_ELDRITCH_HORROR:
			maximumCast = Preferences.getBoolean( "_eldritchHorrorEvoked" ) ? 0 : 1;
			break;

		case SkillPool.STACK_LUMPS:
			maximumCast = 1;
			break;

		}

		return maximumCast;
	}

	@Override
	public String toString()
	{
		if ( this.lastReduction == KoLCharacter.getManaCostAdjustment() && !SkillDatabase.isLibramSkill( this.skillId ) )
		{
			return this.lastStringForm;
		}

		this.lastReduction = KoLCharacter.getManaCostAdjustment();
		int mpCost = SkillDatabase.getMPConsumptionById( this.skillId );
		int advCost = SkillDatabase.getAdventureCost( this.skillId );
		int soulCost = SkillDatabase.getSoulsauceCost( this.skillId );
		int thunderCost = SkillDatabase.getThunderCost( this.skillId );
		int rainCost = SkillDatabase.getRainCost( skillId );
		int lightningCost = SkillDatabase.getLightningCost( skillId );
		int numCosts = 0;
		int itemCost = 0;
		StringBuilder costString = new StringBuilder();
		costString.append( this.skillName );
		costString.append( " (" );
		if ( advCost > 0 )
		{
			costString.append( advCost );
			costString.append( " adv" );
			numCosts++;
		}
		if ( soulCost > 0 )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( soulCost );
			costString.append( " soulsauce" );
			numCosts++;
		}
		if ( this.skillId == SkillPool.SUMMON_ANNOYANCE )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( Preferences.getInteger( "summonAnnoyanceCost" ) );
			costString.append( " swagger" );
			numCosts++;
		}
		if ( this.skillId == SkillPool.HEALING_SALVE )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( "1 white mana" );
			itemCost++;
			numCosts++;
		}
		else if ( this.skillId == SkillPool.DARK_RITUAL )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( "1 black mana" );
			itemCost++;
			numCosts++;
		}
		else if ( this.skillId == SkillPool.LIGHTNING_BOLT_CARD )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( "1 red mana" );
			itemCost++;
			numCosts++;
		}
		else if ( this.skillId == SkillPool.GIANT_GROWTH )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( "1 green mana" );
			itemCost++;
			numCosts++;
		}
		else if ( this.skillId == SkillPool.ANCESTRAL_RECALL )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( "1 blue mana" );
			itemCost++;
			numCosts++;
		}
		if ( thunderCost > 0 )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( thunderCost );
			costString.append( " dB of thunder" );
			numCosts++;
		}
		if ( rainCost > 0 )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( rainCost );
			costString.append( " drops of rain" );
			numCosts++;
		}
		if ( lightningCost > 0 )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( lightningCost );
			costString.append( " bolts of lightning" );
			numCosts++;
		}
		if ( mpCost > 0 || 
			( advCost == 0 && soulCost == 0 && this.skillId != SkillPool.SUMMON_ANNOYANCE &&
			thunderCost == 0 && rainCost == 0 && lightningCost == 0 && itemCost == 0 ) )
		{
			if ( numCosts > 0 )
			{
				costString.append( ", " );
			}
			costString.append( mpCost );
			costString.append( " mp" );
		}
		costString.append( ")" );
		this.lastStringForm = costString.toString();
		return this.lastStringForm;
	}

	private static final boolean canSwitchToItem( final AdventureResult item )
	{
		return !KoLCharacter.hasEquipped( item ) &&
			EquipmentManager.canEquip( item.getName() ) &&
			InventoryManager.hasItem( item, false );
	}

	public static final void optimizeEquipment( final int skillId )
	{
		boolean isBuff = SkillDatabase.isBuff( skillId );

		if ( isBuff )
		{
			if ( SkillDatabase.isTurtleTamerBuff( skillId ) )
			{
				UseSkillRequest.prepareTool( UseSkillRequest.TAMER_TOOLS, skillId );
			}
			else if ( SkillDatabase.isSaucerorBuff( skillId ) )
			{
				UseSkillRequest.prepareTool( UseSkillRequest.SAUCE_TOOLS, skillId );
			}
			else if ( SkillDatabase.isAccordionThiefSong( skillId ) )
			{
				UseSkillRequest.prepareTool( UseSkillRequest.THIEF_TOOLS, skillId );
			}
		}

		if ( Preferences.getBoolean( "switchEquipmentForBuffs" ) )
		{
			UseSkillRequest.reduceManaConsumption( skillId );
		}
	}

	private static final boolean isValidSwitch( final int slotId, final AdventureResult newItem, final int skillId )
	{
		AdventureResult item = EquipmentManager.getEquipment( slotId );
		if ( item.equals( EquipmentRequest.UNEQUIP ) ) return true;

		for ( int i = 0; i < UseSkillRequest.AVOID_REMOVAL.length; ++i )
		{
			if ( item.equals( UseSkillRequest.AVOID_REMOVAL[ i ] ) )
			{
				return false;
			}
		}

		Speculation spec = new Speculation();
		spec.equip( slotId, newItem );
		int[] predictions = spec.calculate().predict();

		// Make sure we do not lose mp in the switch
		if ( KoLCharacter.getCurrentMP() > predictions[ Modifiers.BUFFED_MP ] )
		{
			return false;
		}
		// Make sure we do not reduce max hp in the switch, to avoid loops when casting a heal
		if ( KoLCharacter.getMaximumHP() > predictions[ Modifiers.BUFFED_HP ] )
		{
			return false;
		}
		// Don't allow if we'd lose a song in the switch
		Modifiers mods = spec.getModifiers();
		int predictedSongLimit = 3 + (int) mods.get( Modifiers.ADDITIONAL_SONG ) + ( mods.getBoolean( Modifiers.ADDITIONAL_SONG ) ? 1 : 0 );
		int predictedSongsNeeded = UseSkillRequest.songsActive() + ( UseSkillRequest.newSong( skillId ) ? 1 : 0 );
		if ( predictedSongsNeeded > predictedSongLimit )
		{
			return false;
		}
		return true;
	}

	private static final int attemptSwitch( final int skillId, final AdventureResult item, final boolean slot1Allowed,
		final boolean slot2Allowed, final boolean slot3Allowed )
	{
		if ( slot3Allowed )
		{
			( new EquipmentRequest( item, EquipmentManager.ACCESSORY3 ) ).run();
			return EquipmentManager.ACCESSORY3;
		}

		if ( slot2Allowed )
		{
			( new EquipmentRequest( item, EquipmentManager.ACCESSORY2 ) ).run();
			return EquipmentManager.ACCESSORY2;
		}

		if ( slot1Allowed )
		{
			( new EquipmentRequest( item, EquipmentManager.ACCESSORY1 ) ).run();
			return EquipmentManager.ACCESSORY1;
		}

		return -1;
	}

	private static final void reduceManaConsumption( final int skillId )
	{
		int mpCost = SkillDatabase.getMPConsumptionById( skillId );
		// Never bother trying to reduce mana consumption when casting
		// expensive skills or a libram skill

		if ( mpCost > 50 ||
		     SkillDatabase.isLibramSkill( skillId ) ||
		     mpCost == 0 )
		{
			return;
		}

		// MP is cheap in aftercore, so save server hits
		if ( KoLCharacter.canInteract() )
		{
			return;
		}

		// Try items

		for ( int i = 0; i < UseSkillRequest.AVOID_REMOVAL.length - AVOID_REMOVAL_ONLY; ++i )
		{
			// If you can't reduce cost further, stop
			if ( mpCost == 1 || KoLCharacter.currentNumericModifier( Modifiers.MANA_COST ) <= -3 )
			{
				return;
			}

			// If you haven't got it or can't wear it, don't evaluate further
			if ( !UseSkillRequest.canSwitchToItem( UseSkillRequest.AVOID_REMOVAL[ i ] ) )
			{
				continue;
			}

			// If you won't lose max hp, current mp, or songs, use it
			int slot = EquipmentManager.itemIdToEquipmentType( UseSkillRequest.AVOID_REMOVAL[ i ].getItemId() );
			if ( slot == EquipmentManager.ACCESSORY1 )
			{
				// First determine which slots are available for switching in
				// MP reduction items.  This has do be done inside the loop now
				// that max HP/MP prediction is done, since two changes that are
				// individually harmless might add up to a loss of points.
				boolean slot1Allowed = UseSkillRequest.isValidSwitch( EquipmentManager.ACCESSORY1, UseSkillRequest.AVOID_REMOVAL[ i ], skillId );
				boolean slot2Allowed = UseSkillRequest.isValidSwitch( EquipmentManager.ACCESSORY2, UseSkillRequest.AVOID_REMOVAL[ i ], skillId );
				boolean slot3Allowed = UseSkillRequest.isValidSwitch( EquipmentManager.ACCESSORY3, UseSkillRequest.AVOID_REMOVAL[ i ], skillId );

				UseSkillRequest.attemptSwitch(
					skillId, UseSkillRequest.AVOID_REMOVAL[ i ], slot1Allowed, slot2Allowed, slot3Allowed );
			}
			else
			{
				if ( UseSkillRequest.isValidSwitch( slot, UseSkillRequest.AVOID_REMOVAL[ i ], skillId ) )
				{
					( new EquipmentRequest( UseSkillRequest.AVOID_REMOVAL[ i ], slot ) ).run();
				}
			}

			// Cost may have changed
			mpCost = SkillDatabase.getMPConsumptionById( skillId );
		}
	}

	public static final int songLimit()
	{
		int rv = 3;
		if ( KoLCharacter.currentBooleanModifier( Modifiers.FOUR_SONGS ) )
		{
			++rv;
		}

		rv += KoLCharacter.currentNumericModifier( Modifiers.ADDITIONAL_SONG );

		return rv;
	}

	private static final int songsActive()
	{
		int count = 0;

		AdventureResult[] effects = new AdventureResult[ KoLConstants.activeEffects.size() ];
		KoLConstants.activeEffects.toArray( effects );
		for ( int i = 0; i < effects.length; ++i )
		{
			String skillName = UneffectRequest.effectToSkill( effects[ i ].getName() );
			if ( SkillDatabase.contains( skillName ) )
			{
				int skillId = SkillDatabase.getSkillId( skillName );
				if ( SkillDatabase.isAccordionThiefSong( skillId ) )
				{
					count++;
				}
			}
		}
		return count;
	}

	private static final Boolean newSong( final int skillId )
	{
		if ( !SkillDatabase.isAccordionThiefSong( skillId ) )
		{
			return false;
		}

		AdventureResult[] effects = new AdventureResult[ KoLConstants.activeEffects.size() ];
		KoLConstants.activeEffects.toArray( effects );
		for ( int i = 0; i < effects.length; ++i )
		{
			String skillName = UneffectRequest.effectToSkill( effects[ i ].getName() );
			if ( SkillDatabase.contains( skillName ) )
			{
				int effectSkillId = SkillDatabase.getSkillId( skillName );
				if ( effectSkillId == skillId )
				{
					return false;
				}
			}
		}

		return true;
	}

	@Override
	public void run()
	{
		if ( this.isRunning )
		{
			return;
		}

		if ( GenericRequest.abortIfInFightOrChoice() )
		{
			return;
		}

		UseSkillRequest.lastUpdate = "";

		if ( this.buffCount == 0 )
		{
			// Silently do nothing
			return;
		}

		if ( !KoLCharacter.hasSkill( this.skillName ) )
		{
			UseSkillRequest.lastUpdate = "You don't know how to cast " + this.skillName + ".";
			return;
		}

		// Optimizing equipment can involve changing equipment.
		// Save a checkpoint so we can restore previous equipment.

		UseSkillRequest.optimizeEquipment( this.skillId );

		if ( !KoLmafia.permitsContinue() )
		{
			return;
		}

		int available = this.getMaximumCast();
		if ( available == 0 )
		{
			// We could print something
			return;
		}

		int desired = this.buffCount;
		if ( available < desired )
		{
			// We SHOULD print something here
			KoLmafia.updateDisplay( "(Only " + available + " casts of " + this.skillName + " currently available.)" );
		}

		this.setBuffCount( Math.min( desired, available ) );

		if ( this.skillId == SkillPool.SUMMON_MINION || this.skillId == SkillPool.SUMMON_HORDE )
		{
			ChoiceManager.setSkillUses( this.buffCount );
		}

		this.isRunning = true;
		this.useSkillLoop();
		this.isRunning = false;
	}

	private static final AdventureResult ONCE_CURSED = EffectPool.get( EffectPool.ONCE_CURSED );
	private static final AdventureResult TWICE_CURSED = EffectPool.get( EffectPool.TWICE_CURSED );
	private static final AdventureResult THRICE_CURSED = EffectPool.get( EffectPool.THRICE_CURSED );

	private void useSkillLoop()
	{
		if ( KoLmafia.refusesContinue() )
		{
			return;
		}

		int castsRemaining = this.buffCount;
		if ( castsRemaining == 0 )
		{
			return;
		}

		if ( this.skillId == SkillPool.SHAKE_IT_OFF ||
		     ( this.skillId == SkillPool.BITE_MINION && KoLCharacter.hasSkill( "Devour Minions" ) ) )
		{
			boolean cursed =
				KoLConstants.activeEffects.contains( UseSkillRequest.ONCE_CURSED ) ||
				KoLConstants.activeEffects.contains( UseSkillRequest.TWICE_CURSED ) ||
				KoLConstants.activeEffects.contains( UseSkillRequest.THRICE_CURSED );

			// If on the Hidden Apartment Quest, and have a Curse, and skill will remove it, 
			// ask if you are sure you want to lose it.
			if ( cursed && Preferences.getInteger( "hiddenApartmentProgress" ) < 7 &&
			     !InputFieldUtilities.confirm( "That will remove your Cursed effect. Are you sure?" ) )
			{
				return;
			}
		}

		if ( this.skillId == SkillPool.RAINBOW_GRAVITATION )
		{
			// Acquire necessary wads
			InventoryManager.retrieveItem( ItemPool.COLD_WAD, castsRemaining );
			InventoryManager.retrieveItem( ItemPool.HOT_WAD, castsRemaining );
			InventoryManager.retrieveItem( ItemPool.SLEAZE_WAD, castsRemaining );
			InventoryManager.retrieveItem( ItemPool.SPOOKY_WAD, castsRemaining );
			InventoryManager.retrieveItem( ItemPool.STENCH_WAD, castsRemaining );
			InventoryManager.retrieveItem( ItemPool.TWINKLY_WAD, castsRemaining );
		}

		int mpPerCast = SkillDatabase.getMPConsumptionById( this.skillId );

		// If the skill doesn't use MP then MP restoring and checking can be skipped
		if ( mpPerCast == 0 )
		{
			int soulsauceCost = SkillDatabase.getSoulsauceCost( this.skillId );
			if ( soulsauceCost > 0 && KoLCharacter.getSoulsauce() < soulsauceCost )
			{
				UseSkillRequest.lastUpdate = "Your available soulsauce is too low to cast " + this.skillName + ".";
				KoLmafia.updateDisplay( UseSkillRequest.lastUpdate );
				return;
			}

			int thunderCost = SkillDatabase.getThunderCost( this.skillId );
			if ( thunderCost > 0 && KoLCharacter.getThunder() < thunderCost )
			{
				UseSkillRequest.lastUpdate = "You don't have enough thunder to cast " + this.skillName + ".";
				KoLmafia.updateDisplay( UseSkillRequest.lastUpdate );
				return;
			}

			int rainCost = SkillDatabase.getRainCost( this.skillId );
			if ( rainCost > 0 && KoLCharacter.getRain() < rainCost )
			{
				UseSkillRequest.lastUpdate = "You have insufficient rain drops to cast " + this.skillName + ".";
				KoLmafia.updateDisplay( UseSkillRequest.lastUpdate );
				return;
			}

			int lightningCost = SkillDatabase.getLightningCost( this.skillId );
			if ( lightningCost > 0 && KoLCharacter.getLightning() < lightningCost )
			{
				UseSkillRequest.lastUpdate = "You have too few lightning bolts to cast " + this.skillName + ".";
				KoLmafia.updateDisplay( UseSkillRequest.lastUpdate );
				return;
			}

			boolean single = false;

			AdventureResult mana = SkillDatabase.getManaItemCost( this.skillId );
			if ( mana != null )
			{
				int manaPerCast = mana.getCount();
				int manaNeeded = manaPerCast * castsRemaining;

				// getMaximumCast accounted for the "accessible
				// count" of the appropriate mana before we got
				// here. This should not fail.

				InventoryManager.retrieveItem( mana.getInstance( manaNeeded ) );

				single = true;
			}

			if ( single )
			{
				this.addFormField( this.countFieldId, "1" );
			}
			else
			{
				this.addFormField( this.countFieldId, String.valueOf( castsRemaining ) );
				castsRemaining = 1;
			}

			// Run it via GET
			String URLString = this.getFullURLString();

			this.constructURLString( URLString, false );

			while ( castsRemaining-- > 0  && !KoLmafia.refusesContinue() )
			{
				super.run();
			}

			// But keep fields as per POST for easy modification
			this.constructURLString( URLString, true );

			return;
		}

		// Before executing the skill, recover all necessary mana

		int maximumMP = KoLCharacter.getMaximumMP();
		int maximumCast = maximumMP / mpPerCast;

		// Save name so we can guarantee correct target later
		// *** Why, exactly, is this necessary?
		String originalTarget = this.target;

		// libram skills have variable (increasing) mana cost
		boolean isLibramSkill = SkillDatabase.isLibramSkill( this.skillId );

		while ( castsRemaining > 0 && !KoLmafia.refusesContinue() )
		{
			if ( isLibramSkill )
			{
				mpPerCast = SkillDatabase.getMPConsumptionById( this.skillId );
			}

			if ( maximumMP < mpPerCast )
			{
				UseSkillRequest.lastUpdate = "Your maximum mana is too low to cast " + this.skillName + ".";
				KoLmafia.updateDisplay( UseSkillRequest.lastUpdate );
				return;
			}

			// Find out how many times we can cast with current MP

			int currentCast = this.availableCasts( castsRemaining, mpPerCast );

			// If none, attempt to recover MP in order to cast;
			// take auto-recovery into account.
			// Also recover MP if an opera mask is worn, to maximize its benefit.
			// (That applies only to AT buffs, but it's unlikely that an opera mask
			// will be worn at any other time than casting one.)
			boolean needExtra =
				currentCast < maximumCast &&
				currentCast < castsRemaining &&
				EquipmentManager.getEquipment( EquipmentManager.HAT ).getItemId() == ItemPool.OPERA_MASK;

			if ( currentCast == 0 || needExtra )
			{
				currentCast = Math.min( castsRemaining, maximumCast );
				int currentMP = KoLCharacter.getCurrentMP();

				int recoverMP = mpPerCast * currentCast;

				if ( MoodManager.isExecuting() )
				{
					recoverMP = Math.min( Math.max( recoverMP, MoodManager.getMaintenanceCost() ), maximumMP );
				}

				SpecialOutfit.createImplicitCheckpoint();
				RecoveryManager.recoverMP( recoverMP  );
				SpecialOutfit.restoreImplicitCheckpoint();

				// If no change occurred, that means the person
				// was unable to recover MP; abort the process.

				if ( currentMP == KoLCharacter.getCurrentMP() )
				{
					UseSkillRequest.lastUpdate = "Could not restore enough mana to cast " + this.skillName + ".";
					KoLmafia.updateDisplay( UseSkillRequest.lastUpdate );
					return;
				}

				currentCast = this.availableCasts( castsRemaining, mpPerCast );
			}

			if ( KoLmafia.refusesContinue() )
			{
				UseSkillRequest.lastUpdate = "Error encountered during cast attempt.";
				return;
			}

			// If this happens to be a health-restorative skill,
			// then there is an effective cap based on how much
			// the skill is able to restore.

			switch ( this.skillId )
			{
			case SkillPool.WALRUS_TONGUE:
			case SkillPool.DISCO_NAP:
			case SkillPool.BANDAGES:
			case SkillPool.COCOON:
			case SkillPool.SHAKE_IT_OFF:
			case SkillPool.GELATINOUS_RECONSTRUCTION:

				int healthRestored = HPRestoreItemList.getHealthRestored( this.skillName );
				int maxPossible = Math.max( 1, ( KoLCharacter.getMaximumHP() - KoLCharacter.getCurrentHP() ) / healthRestored );
				castsRemaining = Math.min( castsRemaining, maxPossible );
				currentCast = Math.min( currentCast, castsRemaining );
				break;
			}

			currentCast = Math.min( currentCast, maximumCast );

			if ( currentCast > 0 )
			{
				// Attempt to cast the buff.

				UseSkillRequest.optimizeEquipment( this.skillId );

				if ( KoLmafia.refusesContinue() )
				{
					UseSkillRequest.lastUpdate = "Error encountered during cast attempt.";
					return;
				}

				if ( this.isBuff )
				{
					this.setTarget( originalTarget );
				}

				if ( this.countFieldId != null )
				{
					this.addFormField( this.countFieldId, String.valueOf( currentCast ), false );
				}

				if ( this.target == null || this.target.trim().length() == 0 )
				{
					KoLmafia.updateDisplay( "Casting " + this.skillName + " " + currentCast + " times..." );
				}
				else
				{
					KoLmafia.updateDisplay( "Casting " + this.skillName + " on " + this.target + " " + currentCast + " times..." );
				}

				// Run it via GET
				String URLString = this.getFullURLString();

				this.constructURLString( URLString, false );
				super.run();

				// But keep fields as per POST for easy modification
				this.constructURLString( URLString, true );

				// Otherwise, you have completed the correct
				// number of casts.  Deduct it from the number
				// of casts remaining and continue.

				castsRemaining -= currentCast;
			}
		}

		if ( KoLmafia.refusesContinue() )
		{
			UseSkillRequest.lastUpdate = "Error encountered during cast attempt.";
		}
	}

	public final int availableCasts( int maxCasts, int mpPerCast )
	{
		int availableMP = KoLCharacter.getCurrentMP();
		int currentCast = 0;

		if ( SkillDatabase.isLibramSkill( this.skillId ) )
		{
			currentCast = SkillDatabase.libramSkillCasts( availableMP );
		}
		else if ( SkillDatabase.isSoulsauceSkill( this.skillId ) )
		{
			currentCast = KoLCharacter.getSoulsauce() / SkillDatabase.getSoulsauceCost( this.skillId );
		}
		else if ( SkillDatabase.isThunderSkill( this.skillId ) )
		{
			currentCast = KoLCharacter.getThunder() / SkillDatabase.getThunderCost( this.skillId );
		}
		else if ( SkillDatabase.isRainSkill( this.skillId ) )
		{
			currentCast = KoLCharacter.getRain() / SkillDatabase.getRainCost( this.skillId );
		}
		else if ( SkillDatabase.isLightningSkill( this.skillId ) )
		{
			currentCast = KoLCharacter.getLightning() / SkillDatabase.getLightningCost( this.skillId );
		}
		else
		{
			currentCast = availableMP / mpPerCast;
			currentCast = Math.min( this.getMaximumCast(), currentCast );
		}

		currentCast = Math.min( maxCasts, currentCast );

		return currentCast;
	}

	private static final BuffTool findTool( BuffTool [] tools )
	{
		for ( int i = 0; i < tools.length; ++i )
		{
			BuffTool tool = tools[ i ];
			if ( tool.hasItem( true ) )
			{
				return tool;
			}
		}
		return null;
	}

	public static final boolean hasAccordion()
	{
		return KoLCharacter.canInteract() || UseSkillRequest.findTool( UseSkillRequest.THIEF_TOOLS ) != null;
	}

	public static final boolean hasTotem()
	{
		return KoLCharacter.canInteract() || UseSkillRequest.findTool( UseSkillRequest.TAMER_TOOLS ) != null;
	}

	public static final boolean hasSaucepan()
	{
		return KoLCharacter.canInteract() || UseSkillRequest.findTool( UseSkillRequest.SAUCE_TOOLS ) != null;
	}

	public static final void prepareTool( final BuffTool[] options, int skillId )
	{
		if ( InventoryManager.canUseMall() || InventoryManager.canUseStorage() )
		{
			// If we are here, you are out of Hardcore/Ronin and
			// have access to storage and the mall.

			// Iterate over tools. Retrieve the best one you have
			// available. If you have none available that are better
			// than the default tool, retrieve the default, which
			// is determined using these rules:
			//
			// 1) It is not a quest item and is therefore available
			//    to any class.
			// 2) It is not expensive to buy
			// 3) It provides the most bonus turns of any tool that
			//    satisfies the first two conditions

			for ( BuffTool tool : options )
			{
				// If we have the tool, we are good to go
				if ( tool.hasItem( false ) && ( !tool.isClassLimited() || KoLCharacter.getClassType() == tool.getClassType() ) )
				{
					// If it is not equipped, get it into inventory
					if ( !tool.hasEquipped() )
					{
						tool.retrieveItem();
					}
					return;
				}

				// If we don't have it and this is the default
				// tool on this list, acquire it.
				if ( tool.isDefault() )
				{
					if ( !tool.retrieveItem() )
					{
						KoLmafia.updateDisplay(
							MafiaState.ERROR,
							"You are out of Ronin and need a " + tool.getItem() + " to cast that. Check item retrieval settings." );
					}
					return;
				}
			}
		}

		// If we are here, you are in Hardcore/Ronin and have access
		// only to what is in inventory (or closet, if your retrieval
		// settings allow you to use it).

		// Iterate over items and remember the best one you have available.

		BuffTool bestTool = null;

		for ( BuffTool tool : options )
		{
			if ( tool.hasItem( false ) && ( !tool.isClassLimited() || ( KoLCharacter.getClassType() == tool.getClassType() ) ) )
			{
				bestTool = tool;
				break;
			}
		}

		// If we don't have any of the tools, try to retrieve the
		// weakest one via purchase/sewer fishing.
		if ( bestTool == null )
		{
			BuffTool weakestTool = options[ options.length - 1 ];
			weakestTool.retrieveItem();
			return;
		}

		// if best tool is equipped, cool.
		if ( bestTool.hasEquipped() )
		{
			return;
		}

		// Get best tool into inventory
		bestTool.retrieveItem();
	}

	@Override
	protected boolean retryOnTimeout()
	{
		return false;
	}

	@Override
	protected boolean processOnFailure()
	{
		return true;
	}

	@Override
	public void processResults()
	{
		UseSkillRequest.lastUpdate = "";

		boolean shouldStop = UseSkillRequest.parseResponse( this.getURLString(), this.responseText );

		if ( !UseSkillRequest.lastUpdate.equals( "" ) )
		{
			MafiaState state = shouldStop ? MafiaState.ERROR : MafiaState.CONTINUE;
			KoLmafia.updateDisplay( state, UseSkillRequest.lastUpdate );

			if ( BuffBotHome.isBuffBotActive() )
			{
				BuffBotHome.timeStampedLogEntry( BuffBotHome.ERRORCOLOR, UseSkillRequest.lastUpdate );
			}

			return;
		}

		if ( this.target == null )
		{
			KoLmafia.updateDisplay( this.skillName + " was successfully cast." );
		}
		else
		{
			KoLmafia.updateDisplay( this.skillName + " was successfully cast on " + this.target + "." );
		}
	}

	@Override
	public boolean equals( final Object o )
	{
		return o != null && o instanceof UseSkillRequest && this.getSkillName().equals(
			( (UseSkillRequest) o ).getSkillName() );
	}

	@Override
	public int hashCode()
	{
		return this.skillId;
	}

	public static final UseSkillRequest getUnmodifiedInstance( String skillName )
	{
		if ( skillName == null || !SkillDatabase.contains( skillName ) )
		{
			return null;
		}

		String canonical = StringUtilities.getCanonicalName( skillName );
		UseSkillRequest request = (UseSkillRequest) UseSkillRequest.ALL_SKILLS.get( canonical );
		if ( request == null )
		{
			request = new UseSkillRequest( skillName );
			UseSkillRequest.ALL_SKILLS.put( canonical, request );
		}

		return request;
	}

	public static final UseSkillRequest getUnmodifiedInstance( final int skillId )
	{
		return UseSkillRequest.getUnmodifiedInstance( SkillDatabase.getSkillName( skillId ) );
	}

	public static final UseSkillRequest getInstance( final String skillName, final String target, final int buffCount )
	{
		UseSkillRequest request = UseSkillRequest.getUnmodifiedInstance( skillName );
		if ( request != null )
		{
			request.setTarget( target == null || target.equals( "" ) ? KoLCharacter.getUserName() : target );
			request.setBuffCount( buffCount );
		}
		return request;
	}

	public static final UseSkillRequest getInstance( String skillName )
	{
		return UseSkillRequest.getInstance( skillName, null, 0 );
	}

	public static final UseSkillRequest getInstance( final int skillId )
	{
		return UseSkillRequest.getInstance( SkillDatabase.getSkillName( skillId ) );
	}

	public static final UseSkillRequest getInstance( final String skillName, final int buffCount )
	{
		return UseSkillRequest.getInstance( skillName, null, buffCount );
	}

	public static final UseSkillRequest getInstance( final String skillName, final Concoction conc )
	{
		// Summon Clip Art

		UseSkillRequest request = UseSkillRequest.getUnmodifiedInstance( skillName );
		if ( request != null )
		{
			request.buffCount = 1;
			request.countFieldId = null;
			request.target = null;

			int param = conc.getParam();
			int clip1 = ( param >> 16 ) & 0xFF;
			int clip2 = ( param >>  8 ) & 0xFF;
			int clip3 = ( param       ) & 0xFF;

			request.addFormField( "clip1", String.valueOf( clip1 ) );
			request.addFormField( "clip2", String.valueOf( clip2 ) );
			request.addFormField( "clip3", String.valueOf( clip3 ) );
		}

		return request;
	}

	public static final boolean parseResponse( final String urlString, final String responseText )
	{
		int skillId = UseSkillRequest.lastSkillUsed;
		int count = UseSkillRequest.lastSkillCount;

		if ( urlString.contains( "skillz.php" ) && !urlString.contains( "whichskill" ) )
		{
			// This is a skill list, parse skills for consequences.
			Matcher matcher = UseSkillRequest.SKILLZ_PATTERN.matcher( responseText );
			while ( matcher.find() )
			{
				ConsequenceManager.parseSkillDesc( StringUtilities.parseInt( matcher.group( 1 ) ), matcher.group( 2 ) );
			}
		}

		if ( skillId == -1 )
		{
			UseSkillRequest.lastUpdate = "Skill ID not saved.";
			return false;
		}

		UseSkillRequest.lastSkillUsed = -1;
		UseSkillRequest.lastSkillCount = 0;

		if ( responseText == null || responseText.trim().length() == 0 )
		{
			int initialMP = KoLCharacter.getCurrentMP();
			ApiRequest.updateStatus();

			if ( initialMP == KoLCharacter.getCurrentMP() )
			{
				UseSkillRequest.lastUpdate = "Encountered lag problems.";
				return false;
			}

			UseSkillRequest.lastUpdate = "KoL sent back a blank response, but consumed MP.";
			return true;
		}

		if ( responseText.contains( "You don't have that skill" ) )
		{
			UseSkillRequest.lastUpdate = "That skill is unavailable.";
			return true;
		}

		if ( responseText.contains( "You may only use three Tome summonings each day" ) )
		{
			Preferences.setInteger( "tomeSummons", 3 );
			if ( KoLCharacter.canInteract() )
			{
				switch ( skillId )
				{
				case SkillPool.SNOWCONE:
					Preferences.setInteger( "_snowconeSummons", 3 );
					break;
				case SkillPool.STICKER:
					Preferences.setInteger( "_stickerSummons", 3 );
					break;
				case SkillPool.SUGAR:
					Preferences.setInteger( "_sugarSummons", 3 );
					break;
				case SkillPool.RAD_LIB:
					Preferences.setInteger( "_radlibSummons", 3 );
					break;
				case SkillPool.SMITHSNESS:
					Preferences.setInteger( "_smithsnessSummons", 3 );
					break;
				}
			}
			else
			{
				UseSkillRequest.lastUpdate = "You've used your Tomes enough today.";
			}
			ConcoctionDatabase.setRefreshNeeded( true );
			return true;
		}

		// Summon Clip Art cast through the browser has two phases:
		//
		//   campground.php?preaction=summoncliparts
		//   campground.php?preaction=combinecliparts
		//
		// Only the second once consumes MP and only if it is successful.
		// Internally, we use only the second URL.
		//
		// For now, simply ignore any call on either URL that doesn't
		// result in an item, since failures just redisplay the bookshelf

		if ( skillId == SkillPool.CLIP_ART && !responseText.contains( "You acquire" ) )
		{
			return false;
		}

		// You can't fit anymore songs in your head right now
		// XXX can't fit anymore songs in their head right now.
		if ( responseText.contains( "can't fit anymore songs" ) ||
		     responseText.contains( "can't fit any more songs" ) )
		{
			UseSkillRequest.lastUpdate = "Selected target has the maximum number of AT buffs already.";
			return false;
		}

		if ( responseText.contains( "casts left of the Smile of Mr. A" ) )
		{
			UseSkillRequest.lastUpdate = "You cannot cast that many smiles.";
			return false;
		}

		if ( responseText.contains( "Invalid target player" ) )
		{
			UseSkillRequest.lastUpdate = "Selected target is not a valid target.";
			return true;
		}

		// You can't cast that spell on persons who are lower than
		// level 15, like <name>, who is level 13.
		if ( responseText.contains( "lower than level" ) )
		{
			UseSkillRequest.lastUpdate = "Selected target is too low level.";
			return false;
		}

		if ( responseText.contains( "busy fighting" ) )
		{
			UseSkillRequest.lastUpdate = "Selected target is busy fighting.";
			return false;
		}

		if ( responseText.contains( "receive buffs" ) )
		{
			UseSkillRequest.lastUpdate = "Selected target cannot receive buffs.";
			return false;
		}

		if ( responseText.contains( "You need" ) )
		{
			UseSkillRequest.lastUpdate = "You need special equipment to cast that buff.";
			return true;
		}

		if ( responseText.contains( "You can't remember how to use that skill" ) )
		{
			UseSkillRequest.lastUpdate = "That skill is currently unavailable.";
			return true;
		}

		if ( responseText.contains( "You can't cast this spell because you are not an Accordion Thief" ) )
		{
			UseSkillRequest.lastUpdate = "Only Accordion Thieves can use that skill.";
			return true;
		}

		if ( responseText.contains( "You're already blessed" ) )
		{
			UseSkillRequest.lastUpdate = "You already have that blessing.";
			return true;
		}

		if ( responseText.contains( "not attuned to any particular Turtle Spirit" ) )
		{
			UseSkillRequest.lastUpdate = "You haven't got a Blessing, so can't get a Boon.";
			return true;
		}

		if ( responseText.contains( "You can only declare one Employee of the Month per day" ) )
		{
			UseSkillRequest.lastUpdate = "You can only declare one Employee of the Month per day.";
			Preferences.setBoolean( "_managerialManipulationUsed", true );
			return true;
		}

		// You've already recalled a lot of ancestral memories lately. You should
		// probably give your ancestors the rest of the day off.

		if ( responseText.contains( "You've already recalled a lot of ancestral memories lately" ) )
		{
			UseSkillRequest.lastUpdate = "You can only cast Ancestral Recall 10 times per day.";
			Preferences.setInteger( "_ancestralRecallCasts", 10 );
			return true;
		}

		// You think your stomach has had enough for one day.
		if ( responseText.contains( "enough for one day" ) )
		{
			UseSkillRequest.lastUpdate = "You can only do that once a day.";
			Preferences.setBoolean( "_carboLoaded", true );
			return false;
		}

		// You can't cast that many turns of that skill today. (You've used 5 casts today,
		// and the limit of casts per day you have is 5.)
		if ( responseText.contains( "You can't cast that many turns of that skill today" ) )
		{
			UseSkillRequest.lastUpdate = "You've reached your daily casting limit for that skill.";
			switch ( skillId )
			{
			case SkillPool.THINGFINDER:
				Preferences.setInteger( "_thingfinderCasts", 10 );
				break;

			case SkillPool.BENETTONS:
				Preferences.setInteger( "_benettonsCasts", 10 );
				break;

			case SkillPool.ELRONS:
				Preferences.setInteger( "_elronsCasts", 10 );
				break;

			case SkillPool.COMPANIONSHIP:
				Preferences.setInteger( "_companionshipCasts", 10 );
				break;

			case SkillPool.PRECISION:
				Preferences.setInteger( "_precisionCasts", 10 );
				break;

			case SkillPool.DONHOS:
				Preferences.setInteger( "_donhosCasts", 50 );
				break;

			case SkillPool.INIGOS:
				Preferences.setInteger( "_inigosCasts", 5 );
				break;

			default:
				break;
			}
			return false;
		}

		Matcher limitedMatcher = UseSkillRequest.LIMITED_PATTERN.matcher( responseText );
		// limited-use skills
		// "Y / maxCasts casts used today."
		if ( limitedMatcher.find() )
		{
			int casts = 0;
			// parse the number of casts remaining and set the appropriate preference.

			String numString = limitedMatcher.group( 1 );

			casts = Integer.parseInt( numString );

			switch ( skillId )
			{
			case SkillPool.THINGFINDER:
				Preferences.setInteger( "_thingfinderCasts", casts );
				break;

			case SkillPool.BENETTONS:
				Preferences.setInteger( "_benettonsCasts", casts );
				break;

			case SkillPool.ELRONS:
				Preferences.setInteger( "_elronsCasts", casts );
				break;

			case SkillPool.COMPANIONSHIP:
				Preferences.setInteger( "_companionshipCasts", casts );
				break;

			case SkillPool.PRECISION:
				Preferences.setInteger( "_precisionCasts", casts );
				break;

			case SkillPool.DONHOS:
				Preferences.setInteger( "_donhosCasts", casts );
				break;

			case SkillPool.INIGOS:
				Preferences.setInteger( "_inigosCasts", casts );
				break;
			}
		}

		if ( responseText.contains( "You don't have enough" ) )
		{
			String skillName = SkillDatabase.getSkillName( skillId );

			UseSkillRequest.lastUpdate = "Not enough mana to cast " + skillName + ".";
			ApiRequest.updateStatus();
			return true;
		}

		// The skill was successfully cast. Deal with its effects.
		if ( responseText.contains( "tear the opera mask" ) )
		{
			EquipmentManager.breakEquipment( ItemPool.OPERA_MASK,
				"Your opera mask shattered." );
		}

		int mpCost = SkillDatabase.getMPConsumptionById( skillId ) * count;

		if ( responseText.contains( "You can only conjure" ) ||
		     responseText.contains( "You can only scrounge up" ) ||
		     responseText.contains( "You can't use that skill" ) ||
		     responseText.contains( "You can only summon" ) )
		{
			if ( skillId == SkillPool.COCOON )
			{
				// Cannelloni Cocoon says "You can't use that
				// skill" when you are already at full HP.
				UseSkillRequest.lastUpdate = "You are already at full HP.";
			}
			else if ( skillId == SkillPool.ANCESTRAL_RECALL )
			{
				// Ancestral Recall says "You can't use that
				// skill" if you don't have any blue mana.
				UseSkillRequest.lastUpdate = "You don't have any blue mana.";
				return true;
			}
			else
			{
				UseSkillRequest.lastUpdate = "Summon limit exceeded.";

				// We're out of sync with the actual number of times
				// this skill has been cast.  Adjust the counter by 1
				// at a time.
				count = 1;
			}
			mpCost = 0;
		}

		switch ( skillId )
		{
		case SkillPool.ODE_TO_BOOZE:
			ConcoctionDatabase.getUsables().sort();
			break;

		case SkillPool.WALRUS_TONGUE:
		case SkillPool.DISCO_NAP:
			UneffectRequest.removeEffectsWithSkill( skillId );
			break;

		case SkillPool.SMILE_OF_MR_A:
			Preferences.increment( "_smilesOfMrA", count );
			break;

		case SkillPool.RAGE_GLAND:
			Preferences.setBoolean( "rageGlandVented", true );
			break;

		case SkillPool.RAINBOW_GRAVITATION:

			// Each cast of Rainbow Gravitation consumes five
			// elemental wads and a twinkly wad

			ResultProcessor.processResult( ItemPool.get( ItemPool.COLD_WAD, -count ) );
			ResultProcessor.processResult( ItemPool.get( ItemPool.HOT_WAD, -count ) );
			ResultProcessor.processResult( ItemPool.get( ItemPool.SLEAZE_WAD, -count ) );
			ResultProcessor.processResult( ItemPool.get( ItemPool.SPOOKY_WAD, -count ) );
			ResultProcessor.processResult( ItemPool.get( ItemPool.STENCH_WAD, -count ) );
			ResultProcessor.processResult( ItemPool.get( ItemPool.TWINKLY_WAD, -count ) );

			Preferences.increment( "prismaticSummons", count );
			break;

		case SkillPool.LUNCH_BREAK:
			Preferences.setBoolean( "_lunchBreak", true );
			break;

		case SkillPool.SPAGHETTI_BREAKFAST:
			Preferences.setBoolean( "_spaghettiBreakfast", true );
			break;

		case SkillPool.GRAB_A_COLD_ONE:
			Preferences.setBoolean( "_coldOne", true );
			break;

		case SkillPool.THATS_NOT_A_KNIFE:
			Preferences.setBoolean( "_discoKnife", true );
			break;

		case SkillPool.TURTLE_POWER:
			Preferences.setBoolean( "_turtlePowerCast", true );
			Preferences.setInteger( "turtleBlessingTurns", 0 );
			break;

		case SkillPool.WAR_BLESSING:
		case SkillPool.SHE_WHO_WAS_BLESSING:
		case SkillPool.STORM_BLESSING:
			Preferences.setInteger( "turtleBlessingTurns", 0 );
			break;

		case SkillPool.SUMMON_BONERS:
			Preferences.setBoolean( "_bonersSummoned", true );
			break;

		case SkillPool.REQUEST_SANDWICH:
			// You take a deep breath and prepare for a Boris-style bellow. Then you remember your manners 
			// and shout, "If it's not too much trouble, I'd really like a sandwich right now! Please!" 
			// To your surprise, it works! Someone wanders by slowly and hands you a sandwich, grumbling, 
			// "well, since you asked nicely . . ."
			if ( responseText.contains( "well, since you asked nicely" ) )
			{
				Preferences.setBoolean( "_requestSandwichSucceeded", true );
			}
			break;

		case SkillPool.PASTAMASTERY:
			Preferences.increment( "noodleSummons", count );
			break;

		case SkillPool.CARBOLOADING:
			Preferences.setBoolean( "_carboLoaded", true );
			Preferences.increment( "carboLoading", 1 );
			break;

		case SkillPool.ADVANCED_SAUCECRAFTING:
			Preferences.increment( "reagentSummons", count );
			break;

		case SkillPool.ADVANCED_COCKTAIL:
			Preferences.increment( "cocktailSummons", count );
			break;

		case SkillPool.DEMAND_SANDWICH:
			Preferences.increment( "_demandSandwich", count );
			break;

		case SkillPool.SNOWCONE:
			Preferences.increment( "_snowconeSummons", count );
			Preferences.increment( "tomeSummons", count );
			ConcoctionDatabase.setRefreshNeeded( false );
			break;

		case SkillPool.STICKER:
			Preferences.increment( "_stickerSummons", count );
			Preferences.increment( "tomeSummons", count );
			ConcoctionDatabase.setRefreshNeeded( false );
			break;

		case SkillPool.SUGAR:
			Preferences.increment( "_sugarSummons", count );
			Preferences.increment( "tomeSummons", count );
			ConcoctionDatabase.setRefreshNeeded( false );
			break;

		case SkillPool.CLIP_ART:
			Preferences.increment( "_clipartSummons", count );
			Preferences.increment( "tomeSummons", count );
			ConcoctionDatabase.setRefreshNeeded( false );
			break;

		case SkillPool.RAD_LIB:
			Preferences.increment( "_radlibSummons", count );
			Preferences.increment( "tomeSummons", count );
			ConcoctionDatabase.setRefreshNeeded( false );
			break;

		case SkillPool.SMITHSNESS:
			Preferences.increment( "_smithsnessSummons", count );
			Preferences.increment( "tomeSummons", count );
			ConcoctionDatabase.setRefreshNeeded( false );
			break;

		case SkillPool.HILARIOUS:
			Preferences.increment( "grimoire1Summons", 1 );
			break;

		case SkillPool.TASTEFUL:
			Preferences.increment( "grimoire2Summons", 1 );
			break;

		case SkillPool.CARDS:
			Preferences.increment( "grimoire3Summons", 1 );
			break;

		case SkillPool.GEEKY:
			Preferences.increment( "_grimoireGeekySummons", 1 );
			break;

		case SkillPool.CONFISCATOR:
			Preferences.increment( "_grimoireConfiscatorSummons", 1 );
			break;

		case SkillPool.CRIMBO_CANDY:
			Preferences.increment( "_candySummons", 1 );
			break;

		case SkillPool.PSYCHOKINETIC_HUG:
			Preferences.setBoolean( "_psychokineticHugUsed", true );
			break;

		case SkillPool.MANAGERIAL_MANIPULATION:
			Preferences.setBoolean( "_managerialManipulationUsed", true );
			break;

		case SkillPool.CONJURE_EGGS:
			Preferences.setBoolean( "_jarlsEggsSummoned", true );
			break;
		case SkillPool.CONJURE_DOUGH:
			Preferences.setBoolean( "_jarlsDoughSummoned", true );
			break;
		case SkillPool.CONJURE_VEGGIES:
			Preferences.setBoolean( "_jarlsVeggiesSummoned", true );
			break;
		case SkillPool.CONJURE_CHEESE:
			Preferences.setBoolean( "_jarlsCheeseSummoned", true );
			break;
		case SkillPool.CONJURE_MEAT:
			Preferences.setBoolean( "_jarlsMeatSummoned", true );
			break;
		case SkillPool.CONJURE_POTATO:
			Preferences.setBoolean( "_jarlsPotatoSummoned", true );
			break;
		case SkillPool.CONJURE_CREAM:
			Preferences.setBoolean( "_jarlsCreamSummoned", true );
			break;
		case SkillPool.CONJURE_FRUIT:
			Preferences.setBoolean( "_jarlsFruitSummoned", true );
			break;

		case SkillPool.EGGMAN:
			ResultProcessor.removeItem( ItemPool.COSMIC_EGG );
			break;
		case SkillPool.RADISH_HORSE:
			ResultProcessor.removeItem( ItemPool.COSMIC_VEGETABLE );
			break;
		case SkillPool.HIPPOTATO:
			ResultProcessor.removeItem( ItemPool.COSMIC_POTATO );
			break;
		case SkillPool.CREAMPUFF:
			ResultProcessor.removeItem( ItemPool.COSMIC_CREAM );
			break;

		case SkillPool.DEEP_VISIONS:
			DreadScrollManager.handleDeepDarkVisions( responseText );
			break;

		case SkillPool.BIND_VAMPIEROGHI:
		case SkillPool.BIND_VERMINCELLI:
		case SkillPool.BIND_ANGEL_HAIR_WISP:
		case SkillPool.BIND_UNDEAD_ELBOW_MACARONI:
		case SkillPool.BIND_PENNE_DREADFUL:
		case SkillPool.BIND_LASAGMBIE:
		case SkillPool.BIND_SPICE_GHOST:
		case SkillPool.BIND_SPAGHETTI_ELEMENTAL:
			PastaThrallData.handleBinding( skillId, responseText );
			break;

		case SkillPool.DISMISS_PASTA_THRALL:
			PastaThrallData.handleDismissal( responseText );
			break;

		case SkillPool.THROW_PARTY:
			Preferences.setBoolean( "_petePartyThrown", true );
			break;
		case SkillPool.INCITE_RIOT:
			Preferences.setBoolean( "_peteRiotIncited", true );
			break;

		case SkillPool.SUMMON_ANNOYANCE:
			Preferences.setBoolean( "_summonAnnoyanceUsed", true );
			Preferences.decrement( "availableSwagger", Preferences.getInteger( "summonAnnoyanceCost" ) );
			break;

		case SkillPool.PIRATE_BELLOW:
			Preferences.setBoolean( "_pirateBellowUsed", true );
			break;

		case SkillPool.HOLIDAY_FUN:
			Preferences.setBoolean( "_holidayFunUsed", true );
			break;

		case SkillPool.SUMMON_CARROT:
			Preferences.setBoolean( "_summonCarrotUsed", true );
			break;

		case SkillPool.SUMMON_KOKOMO_RESORT_PASS:
			Preferences.setBoolean( "_summonResortPassUsed", true );
			break;

		case SkillPool.ANCESTRAL_RECALL:
			Preferences.increment( "_ancestralRecallCasts", count );
			ResultProcessor.processResult( ItemPool.get( ItemPool.BLUE_MANA, -count ) );
			break;

		case SkillPool.DARK_RITUAL:
			ResultProcessor.processResult( ItemPool.get( ItemPool.BLACK_MANA, -count ) );
			break;

		case SkillPool.PERFECT_FREEZE:
			Preferences.setBoolean( "_perfectFreezeUsed", true );
			break;

		case SkillPool.COMMUNISM:
			Preferences.setBoolean( "_communismUsed", true );
			break;

		case SkillPool.BOW_LEGGED_SWAGGER:
			Preferences.setBoolean( "_bowleggedSwaggerUsed", true );
			break;

		case SkillPool.BEND_HELL:
			Preferences.setBoolean( "_bendHellUsed", true );
			break;

		case SkillPool.STEELY_EYED_SQUINT:
			Preferences.setBoolean( "_steelyEyedSquintUsed", true );
			break;

		case SkillPool.CECI_CHAPEAU:
			Preferences.setBoolean( "_ceciHatUsed", true );
			break;

		case SkillPool.STACK_LUMPS:
			Preferences.increment( "_stackLumpsUses" );
			ResultProcessor.processResult( ItemPool.get( ItemPool.NEGATIVE_LUMP, -100 ) );
			break;

		case SkillPool.EVOKE_ELDRITCH_HORROR:
			Preferences.setBoolean( "_eldritchHorrorEvoked", true );
			break;
		}

		if ( SkillDatabase.isLibramSkill( skillId ) )
		{
			int cast = Preferences.getInteger( "libramSummons" );
			mpCost = SkillDatabase.libramSkillMPConsumption( cast + 1, count );
			Preferences.increment( "libramSummons", count );
			LockableListFactory.sort( KoLConstants.summoningSkills );
			LockableListFactory.sort( KoLConstants.usableSkills );
		}

		else if ( SkillDatabase.isSoulsauceSkill( skillId ) )
		{
			KoLCharacter.decrementSoulsauce( SkillDatabase.getSoulsauceCost( skillId ) * count );
		}

		else if ( SkillDatabase.isThunderSkill( skillId ) )
		{
			KoLCharacter.decrementThunder( SkillDatabase.getThunderCost( skillId ) * count );
		}

		else if ( SkillDatabase.isRainSkill( skillId ) )
		{
			KoLCharacter.decrementRain( SkillDatabase.getRainCost( skillId ) * count );
		}

		else if ( SkillDatabase.isLightningSkill( skillId ) )
		{
			KoLCharacter.decrementLightning( SkillDatabase.getLightningCost( skillId ) * count );
		}

		if ( mpCost > 0 )
		{
			ResultProcessor.processResult( new AdventureResult( AdventureResult.MP, 0 - mpCost ) );
		}

		return false;
	}

	public static int getSkillId( final String urlString )
	{
		Matcher skillMatcher = UseSkillRequest.SKILLID_PATTERN.matcher( urlString );
		if ( skillMatcher.find() )
		{
			return StringUtilities.parseInt( skillMatcher.group( 1 ) );
		}

		skillMatcher = UseSkillRequest.BOOKID_PATTERN.matcher( urlString );
		if ( !skillMatcher.find() )
		{
			return -1;
		}

		String action = skillMatcher.group( 1 );

		if ( action.equals( "snowcone" ) )
		{
			return SkillPool.SNOWCONE;
		}

		if ( action.equals( "stickers" ) )
		{
			return SkillPool.STICKER;
		}

		if ( action.equals( "sugarsheets" ) )
		{
			return SkillPool.SUGAR;
		}

		if ( action.equals( "cliparts" ) )
		{
			if ( !urlString.contains( "clip3=" ) )
			{
				return -1;
			}

			return SkillPool.CLIP_ART;
		}

		if ( action.equals( "radlibs" ) )
		{
			return SkillPool.RAD_LIB;
		}

		if ( action.equals( "smithsness" ) )
		{
			return SkillPool.SMITHSNESS;
		}

		if ( action.equals( "hilariousitems" ) )
		{
			return SkillPool.HILARIOUS;
		}

		if ( action.equals( "spencersitems" ) )
		{
			return SkillPool.TASTEFUL;
		}

		if ( action.equals( "aa" ) )
		{
			return SkillPool.CARDS;
		}

		if ( action.equals( "thinknerd" ) )
		{
			return SkillPool.GEEKY;
		}

		if ( action.equals( "candyheart" ) )
		{
			return SkillPool.CANDY_HEART;
		}

		if ( action.equals( "partyfavor" ) )
		{
			return SkillPool.PARTY_FAVOR;
		}

		if ( action.equals( "lovesongs" ) )
		{
			return SkillPool.LOVE_SONG;
		}

		if ( action.equals( "brickos" ) )
		{
			return SkillPool.BRICKOS;
		}

		if ( action.equals( "gygax" ) )
		{
			return SkillPool.DICE;
		}

		if ( action.equals( "resolutions" ) )
		{
			return SkillPool.RESOLUTIONS;
		}

		if ( action.equals( "taffy" ) )
		{
			return SkillPool.TAFFY;
		}

		if ( action.equals( "confiscators" ) )
		{
			return SkillPool.CONFISCATOR;
		}

		return -1;
	}
	
	private static final int getCount( final String urlString, int skillId )
	{
		Matcher countMatcher = UseSkillRequest.COUNT_PATTERN.matcher( urlString );

		if ( !countMatcher.find() )
		{
			return 1;
		}

		int availableMP = KoLCharacter.getCurrentMP();
		int maxcasts;
		if ( SkillDatabase.isLibramSkill( skillId ) )
		{
			maxcasts = SkillDatabase.libramSkillCasts( availableMP );
		}
		else if ( SkillDatabase.isSoulsauceSkill( skillId ) )
		{
			maxcasts = KoLCharacter.getSoulsauce() / SkillDatabase.getSoulsauceCost( skillId );
		}
		else if ( SkillDatabase.isThunderSkill( skillId ) )
		{
			maxcasts = KoLCharacter.getThunder() / SkillDatabase.getThunderCost( skillId );
		}
		else if ( SkillDatabase.isRainSkill( skillId ) )
		{
			maxcasts = KoLCharacter.getRain() / SkillDatabase.getRainCost( skillId );
		}
		else if ( SkillDatabase.isLightningSkill( skillId ) )
		{
			maxcasts = KoLCharacter.getLightning() / SkillDatabase.getLightningCost( skillId );
		}
		else
		{
			int MP = SkillDatabase.getMPConsumptionById( skillId );
			maxcasts = SkillDatabase.getMaxCasts( skillId );
			maxcasts = maxcasts == -1 ? Integer.MAX_VALUE : maxcasts;
			if ( MP != 0 )
			{
				maxcasts = Math.min( maxcasts, availableMP / MP );
			}
		}

		if ( countMatcher.group( 1 ).startsWith( "*" ) )
		{
			return maxcasts;
		}

		return Math.min( maxcasts, StringUtilities.parseInt( countMatcher.group( 1 ) ) );
	}

	public static final boolean registerRequest( final String urlString )
	{
		if ( urlString.startsWith( "skillz.php" ) )
		{
			return true;
		}

		if ( !urlString.startsWith( "campground.php" ) && !urlString.startsWith( "runskillz.php" ) )
		{
			return false;
		}

		int skillId = UseSkillRequest.getSkillId( urlString );
		// Quick skills has (select a skill) with ID = 999
		if ( skillId == -1 || skillId == 999 )
		{
			return false;
		}

		int count = UseSkillRequest.getCount( urlString, skillId );
		String skillName = SkillDatabase.getSkillName( skillId );

		UseSkillRequest.lastSkillUsed = skillId;
		UseSkillRequest.lastSkillCount = count;

		RequestLogger.updateSessionLog();
		RequestLogger.updateSessionLog( "cast " + count + " " + skillName );

		SkillDatabase.registerCasts( skillId, count );

		return true;
	}

	@Override
	public int getAdventuresUsed()
	{
		return SkillDatabase.getAdventureCost( this.skillId );
	}

	public static int getAdventuresUsed( final String urlString )
	{
		return SkillDatabase.getAdventureCost( UseSkillRequest.getSkillId( urlString ) );
	}

	public static class BuffTool
	{
		final AdventureResult item;
		final int bonusTurns;
		final boolean def;
		final String classType;

		public BuffTool( final int itemId, final int bonusTurns, final boolean def, final String classType )
		{
			this.item = ItemPool.get( itemId, 1 );
			this.bonusTurns = bonusTurns;
			this.def = def;
			this.classType = classType;
		}

		public final AdventureResult getItem()
		{
			return this.item;
		}

		public final int getBonusTurns()
		{
			return this.bonusTurns;
		}

		public final boolean isClassLimited()
		{
			return this.classType != null;
		}

		public final String getClassType()
		{
			return this.classType;
		}

		public final boolean isDefault()
		{
			return this.def;
		}

		public final boolean hasEquipped()
		{
			return KoLCharacter.hasEquipped( this.item );
		}

		public final boolean hasItem( final boolean create )
		{
			return InventoryManager.hasItem( this.item, create );
		}

		public final boolean retrieveItem()
		{
			return InventoryManager.retrieveItem( this.item );
		}
	}
}
