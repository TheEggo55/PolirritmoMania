package polyrhythmmania.storymode.contract

import com.badlogic.gdx.Gdx
import paintbox.binding.toConstVar
import polyrhythmmania.editor.block.BlockEndState
import polyrhythmmania.engine.tempo.TempoChange
import polyrhythmmania.storymode.contract.Contract.Companion.NOT_ALLOWED_TO_SKIP
import polyrhythmmania.storymode.gamemode.*
import polyrhythmmania.storymode.gamemode.boss.StoryBossGameMode
import polyrhythmmania.storymode.inbox.InboxItem
import polyrhythmmania.storymode.inbox.InboxItems


object Contracts {

    const val ID_TUTORIAL1: String = "tutorial1"
    const val ID_BOSS: String = "boss"

    val contracts: Map<String, Contract>

    init {
        this.contracts = mutableMapOf()

        fun add(contract: Contract) {
            this.contracts[contract.id] = contract
        }

        add(Contract(ID_TUTORIAL1, Requester.POLYRHYTHM_INC, JingleType.GBA, null, 0, noListingName = true, skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            Tutorial1GameMode(main)
        })
        add(Contract(ID_BOSS, Requester.TIM, JingleType.NONE, Attribution(SongInfo.prmaniaGeneric("MODE: Final Boss"), listOf("GENERIC", "chrislo27", "Luxury")), 0, skipAfterNFailures = NOT_ALLOWED_TO_SKIP, gamemodeFactory = StoryBossGameMode.getFactory()))

        add(Contract("air_rally", Requester.SHIPSTEERING, JingleType.GBA, Attribution(SongInfo.megamix("Air Rally"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/air_rally.prmproj"))
        })
        add(Contract("air_rally_2", Requester.SHIPSTEERING, JingleType.MODERN, Attribution(SongInfo.fever("Air Rally", listOf(SongNameAndSource.megamix("Air Rally 2"))), listOf("GENERIC")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/air_rally_2.prmproj"))
        })
        add(Contract("boosted_tweezers", Requester.CUBE_ROOT, JingleType.GBA, Attribution(SongInfo.tengoku("Rhythm Tweezers", listOf(SongNameAndSource.megamix("Rhythm Tweezers 2"))), listOf("Kievit")), 75) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/boosted_tweezers.prmproj"))
        })
        add(Contract("bouncy_road", Requester.POLYRHYTHM_INC, JingleType.MODERN, Attribution(SongInfo.tengoku("Bouncy Road"), listOf("J-D Thunder", "Kievit")), 60) { main ->
            StoryAsmGameModeBouncyRoad(main)
        })
        add(Contract("bouncy_road_2", Requester.POLYBUILD, JingleType.MODERN, Attribution(SongInfo.tengoku("Bouncy Road"), listOf("chrislo27", "Kievit")), 60) { main ->
            StoryAsmGameModeBouncyRoad2(main)
        })
        add(Contract("built_to_scale_ds", Requester.POLYBUILD, JingleType.GBA, Attribution(SongInfo.ds("Built to Scale"), listOf("Kievit")), 65) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/built_to_scale_ds.prmproj"))
        })
        add(Contract("bunny_hop", Requester.MOON_BUNNY, JingleType.GBA, Attribution(SongInfo.tengoku("Bunny Hop"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/bunny_hop.prmproj"))
        })
        add(Contract("crop_stomp", Requester.STOMP_CHOMP_AGRI, JingleType.GBA, Attribution(SongInfo.ds("Crop Stomp"), listOf("Kievit")), 75, skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/crop_stomp.prmproj"))
        })
        add(Contract("fillbots", Requester.BUILDROID, JingleType.GBA, Attribution(SongInfo.megamix("Fillbots"), listOf("J-D Thunder")), 60, skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/fillbots.prmproj"))
        })
        add(Contract("fillbots2", Requester.BUILDROID, JingleType.GBA, Attribution(SongInfo.ds("Fillbots", listOf(SongNameAndSource.megamix("Fillbots 2"))), listOf("J-D Thunder")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/fillbots2.prmproj"))
        })
        add(Contract("first_contact", Requester.ALIENS, JingleType.ARCADE, Attribution(SongInfo.megamix("First Contact"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/first_contact.prmproj"))
        })
        add(Contract("flock_step", Requester.DIYRE, JingleType.MODERN, Attribution(SongInfo.fever("Flock Step"), listOf("Kievit", "Luxury")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/flock_step.prmproj"))
        })
        add(Contract("fork_lifter", Requester.PEAS, JingleType.MODERN, Attribution(SongInfo.fever("Fork Lifter"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/fork_lifter.prmproj"))
        })
        add(Contract("fruit_basket", Requester.POLYRHYTHM_INC, JingleType.GBA, Attribution(SongInfo.megamix("Fruit Basket"), listOf("chrislo27")), 60) { main ->
            StoryDunkGameModeFruitBasket(main)
        })
        add(Contract("fruit_basket_2", Requester.POLYBUILD, JingleType.GBA, Attribution(SongInfo.megamix("Fruit Basket 2"), listOf("J-D Thunder", "chrislo27")), 60) { main ->
            StoryDunkGameModeFruitBasket2(main)
        })
        add(Contract("hole_in_one", Requester.POLYRHYTHM_INC, JingleType.GBA, Attribution(SongInfo.fever("Hole in One"), listOf("J-D Thunder", "chrislo27")), 60) { main ->
            StoryDunkGameModeHoleInOne(main)
        })
        add(Contract("hole_in_one_2", Requester.POLYBUILD, JingleType.GBA, Attribution(SongInfo.fever("Hole in One 2"), listOf("Lvl100Feraligatr", "chrislo27")), 60) { main ->
            StoryDunkGameModeHoleInOne2(main)
        })
        add(Contract("monkey_watch", Requester.POLYBUILD, JingleType.MODERN, Attribution(SongInfo.fever("Monkey Watch"), listOf("Kievit")), 60) { main ->
            StoryAsmGameModeMonkeyWatch(main)
        })
        add(Contract("rhythm_rally", Requester.GOOD_SPORTS, JingleType.GBA, Attribution(SongInfo.megamix("Rhythm Rally"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/rhythm_rally.prmproj"))
        })
        add(Contract("rhythm_rally_2", Requester.GOOD_SPORTS, JingleType.ARCADE, Attribution(SongInfo.ds("Rhythm Rally", listOf(SongNameAndSource.megamix("Rhythm Rally 2"))), listOf("Kievit")), 60, listOf(Condition.DefectiveRods(3))) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/rhythm_rally_2.prmproj"))
        })
        add(Contract("rhythm_tweezers", Requester.CUBE_ROOT, JingleType.GBA, Attribution(SongInfo.megamix("Rhythm Tweezers"), listOf("Kievit")), 70, skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/rhythm_tweezers.prmproj"))
        })
        add(Contract("rhythm_tweezers_2", Requester.CUBE_ROOT, JingleType.GBA, Attribution(SongInfo.tengoku("Rhythm Tweezers", listOf(SongNameAndSource.megamix("Rhythm Tweezers 2"))), listOf("Kievit")), 70) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/rhythm_tweezers_2.prmproj"))
        })
        add(Contract("ringside", Requester.KRIQ, JingleType.MODERN, Attribution(SongInfo.fever("Ringside"), listOf("Dream Top", "Conn")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/ringside.prmproj"))
        })
        add(Contract("screwbots", Requester.BUILDROID, JingleType.GBA, Attribution(SongInfo.fever("Screwbot Factory"), listOf("Kievit")), 75) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/screwbots.prmproj"))
        })
        add(Contract("screwbots2", Requester.POLYBUILD, JingleType.GBA, Attribution(SongInfo.fever("Screwbot Factory 2"), listOf("Kievit")), 75) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/screwbots2.prmproj"))
        })
        add(Contract("second_contact", Requester.ALIENS, JingleType.ARCADE, Attribution(SongInfo.megamix("Second Contact"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/second_contact.prmproj"))
        })
        add(Contract("shootemup", Requester.LOCKSTEP_MARTIAN, JingleType.ARCADE, Attribution(SongInfo.megamix("Shoot-'em-up"), listOf("Huebird")), 60, skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/shootemup.prmproj"))
        })
        add(Contract("spaceball", Requester.ALIENS, JingleType.GBA, Attribution(SongInfo.tengoku("Spaceball"), listOf("spoopster")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/spaceball.prmproj"))
        })
        add(Contract("super_samurai_slice", Requester.GAME_DEV, JingleType.GBA, Attribution(SongInfo.megamix("Super Samurai Slice"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/super_samurai_slice.prmproj"))
        })
        add(Contract("super_samurai_slice_2", Requester.GAME_DEV, JingleType.GBA, Attribution(SongInfo.megamix("Super Samurai Slice 2"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/super_samurai_slice_2.prmproj"))
        })
        add(Contract("tap_trial", Requester.ANIMAL_ACROBATICS, JingleType.GBA, Attribution(SongInfo.tengoku("Tap Trial"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/tap_trial.prmproj"))
        })
        add(Contract("tap_trial_2", Requester.ANIMAL_ACROBATICS, JingleType.GBA, Attribution(SongInfo.tengoku("Tap Trial 2"), listOf("Kievit")), 65) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/tap_trial_2.prmproj"))
        })
        add(Contract("toss_boys", Requester.TOSS_BOYS, JingleType.GBA, Attribution(SongInfo.tengoku("トスボーイズ (Toss Boys)"), listOf("Dream Top")), 0, listOf(Condition.Lives(3))) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/toss_boys.prmproj")).apply {
                val livesMode = this.container.engine.modifiers.livesMode
                livesMode.maxLives.set(3)
                livesMode.enabled.set(true)
            }
        })
        add(Contract("tram_and_pauline", Requester.ANIMAL_ACROBATICS, JingleType.GBA, Attribution(SongInfo.tengoku("トランとポリン (Tram and Pauline)", songNameWithLineBreaks = "トランとポリン\n(Tram and Pauline)"), listOf("J-D Thunder", "Conn")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/tram_and_pauline.prmproj"))
        })
        add(Contract("working_dough", Requester.DOUGH, JingleType.MODERN, Attribution(SongInfo.fever("Working Dough"), listOf("J-D Thunder")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/working_dough.prmproj"))
        })
        add(Contract("working_dough_2", Requester.DOUGH, JingleType.MODERN, Attribution(SongInfo.fever("Working Dough 2"), listOf("Kievit")), 60) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/working_dough_2.prmproj"))
        })

        // Super Hard! contracts
        add(Contract("air_rally_superhard", Requester.SHIPSTEERING, JingleType.GBA, Attribution(SongInfo.megamix("Air Rally"), listOf("Kievit")), 35, extraConditions = listOf(Condition.InputRestriction.AcesOnly), skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/superhard/air_rally.prmproj"), percentSpeed = 100)
        })
        add(Contract("bouncy_road_2_superhard", Requester.POLYBUILD, JingleType.MODERN, Attribution(SongInfo.tengoku("Bouncy Road"), listOf("chrislo27", "Kievit")), 70, skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryAsmGameModeBouncyRoad2Hard(main)
        })
        add(Contract("built_to_scale_ds_superhard", Requester.POLYBUILD, JingleType.GBA, Attribution(SongInfo.ds("Built to Scale"), listOf("Kievit")), 0, extraConditions = listOf(Condition.TempoUp(125), Condition.Lives(5), Condition.InputRestriction.NoBarelies), skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/superhard/built_to_scale_ds.prmproj"), percentSpeed = 125)
        })
        add(Contract("bunny_hop_superhard", Requester.MOON_BUNNY, JingleType.GBA, Attribution(SongInfo.tengoku("Bunny Hop"), listOf("Kievit")), 0, extraConditions = listOf(Condition.TempoUp(110), Condition.Lives(2)), skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/superhard/bunny_hop.prmproj"), percentSpeed = 110)
        })
        add(Contract("crop_stomp_superhard", Requester.STOMP_CHOMP_AGRI, JingleType.GBA, Attribution(SongInfo.ds("Crop Stomp"), listOf("Kievit")), 0, extraConditions = listOf(Condition.TempoUp(150), Condition.MonsterGoal), skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/superhard/crop_stomp.prmproj"), percentSpeed = 150)
        })
//        add(Contract("fillbots2_superhard", Requester.POLYBUILD, JingleType.GBA, Attribution(SongInfo.ds("Fillbots", listOf(SongNameAndSource.megamix("Fillbots 2"))), listOf("J-D Thunder", "Kievit")), 0, extraConditions = listOf(Condition.TempoUp(150), Condition.Lives(3)), skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
//            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/superhard/fillbots2.prmproj"), percentSpeed = 150)
//        })
        add(Contract("fillbots3_superhard", Requester.POLYBUILD, JingleType.GBA, Attribution(SongInfo.ds("Fillbots 2"), listOf("J-D Thunder", "Kievit")), 0, extraConditions = listOf(Condition.TempoUp(150), Condition.Lives(4)), skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/superhard/fillbots3.prmproj"), percentSpeed = 150)
        })
        add(Contract("first_contact_superhard", Requester.ALIENS, JingleType.ARCADE, Attribution(SongInfo.megamix("First Contact"), listOf("Kievit")), 75, extraConditions = listOf(Condition.TempoUp(125)), skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/superhard/first_contact.prmproj"), percentSpeed = 125)
        })
        add(Contract("screwbots_superhard", Requester.POLYBUILD, JingleType.GBA, Attribution(SongInfo.fever("Screwbot Factory"), listOf("Kievit")), 80, extraConditions = listOf(Condition.TempoUp(125)), skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/superhard/screwbots.prmproj"), percentSpeed = 125)
        })
        add(Contract("tap_trial_2_superhard", Requester.ANIMAL_ACROBATICS, JingleType.GBA, Attribution(SongInfo.tengoku("Tap Trial 2"), listOf("Kievit", "Conn")), 0, extraConditions = listOf(Condition.TempoUp(110), Condition.MonsterGoal), skipAfterNFailures = NOT_ALLOWED_TO_SKIP) { main ->
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/superhard/tap_trial_2.prmproj"), percentSpeed = 110)
        })

        
        // Debug contracts
        add(Contract("air_rally_one_life", Requester.DEBUG, JingleType.GBA, contracts["air_rally"]?.attribution, 0, listOf(Condition.Lives(1))) { main ->
            // FIXME this is a debug contract
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/air_rally.prmproj")).apply {
                val lives = this.engine.modifiers.livesMode
                lives.maxLives.set(1)
                lives.enabled.set(true)
            }
        })
        add(Contract("air_rally_earlyend_instantpass", Requester.DEBUG, JingleType.GBA, contracts["air_rally"]?.attribution, 0) { main ->
            // FIXME this is a debug contract
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/air_rally.prmproj")).apply {
                this.container.addBlock(BlockEndState(this.engine).apply {
                    this.beat = 16f
                })
            }
        })
        add(Contract("air_rally_earlyend_50pass", Requester.DEBUG, JingleType.GBA, contracts["air_rally"]?.attribution, 50) { main ->
            // FIXME this is a debug contract
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/air_rally.prmproj")).apply {
                this.container.addBlock(BlockEndState(this.engine).apply {
                    this.beat = 16f
                })
            }
        })
        add(Contract("air_rally_2_monster", Requester.DEBUG, JingleType.GBA, contracts["air_rally_2"]?.attribution, 60, listOf(Condition.MonsterGoal)) { main ->
            // FIXME this is a debug contract
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/air_rally_2.prmproj")).apply {
                val monsterGoal = this.container.engine.modifiers.monsterGoal
                monsterGoal.enabled.set(true)
            }
        })
        add(Contract("air_rally_2_monster_super_hard", Requester.DEBUG, JingleType.GBA, contracts["air_rally_2"]?.attribution, 60, listOf(Condition.MonsterGoal)) { main ->
            // FIXME this is a debug contract
            StoryGameModeFromFile(main, Gdx.files.internal("story/levels/air_rally_2.prmproj")).apply {
                this.engine.tempos.addTempoChange(TempoChange(0f, 182f * 2))
                this.engine.tempos.addTempoChange(TempoChange(4f, 182f))

                val monsterGoal = this.container.engine.modifiers.monsterGoal
                monsterGoal.difficulty.set(150f)
                monsterGoal.enabled.set(true)
            }
        })
    }

    operator fun get(id: String): Contract = contracts.getValue(id)


    object DebugInboxItems : InboxItems() {
        init {
            val toAdd = mutableListOf<InboxItem>()

            for (contract in Contracts.contracts.values.sortedBy { it.id }) {
                if (contract.id == "tutorial1") {
                    continue
                }
                val listingName = contract.id.toConstVar()
                toAdd += InboxItem.ContractDoc(contract, itemID = "debugcontr_${contract.id}", listingName = listingName, name = listingName)
            }

            this.setItems(toAdd)
        }
    }

}