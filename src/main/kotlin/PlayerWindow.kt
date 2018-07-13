import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.text.Text
import javafx.stage.FileChooser
import javafx.util.Duration
import tornadofx.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class PlayerWindow : View("BitPlayer") {
    override val root: VBox by fxml()
    private val startButton: Button by fxid("startButton")
    private val nextButton: Button by fxid("nextButton")
    private val prevButton: Button by fxid("prevButton")
    private val stopButton: Button by fxid("stopButton")

    private val addFileButton: Button by fxid("addFileButton")
    private val addFolderButton: Button by fxid("addFolderButton")
    private val removeFileButton: Button by fxid("removeFileButton")
    private val removeAllButton: Button by fxid("removeAllButton")

    private val musicVisual1: ProgressBar by fxid("musicVisual1")
    private val musicVisual2: ProgressBar by fxid("musicVisual2")
    private val musicVisual3: ProgressBar by fxid("musicVisual3")
    private val musicVisual4: ProgressBar by fxid("musicVisual4")
    private val musicVisual5: ProgressBar by fxid("musicVisual5")
    private val musicVisual6: ProgressBar by fxid("musicVisual6")
    private val musicVisual7: ProgressBar by fxid("musicVisual7")
    private val musicVisual8: ProgressBar by fxid("musicVisual8")

    private val songProgress: Slider by fxid("songProgress")
    private val volumeControl: Slider by fxid("volumeControl")
    private val currentSong: Label by fxid("currentSong")
    private val currentTime: Label by fxid("currentTime")

    private val songsList: ListView<Text> by fxid("songsList")

    private val musicList = ArrayList<Media>()
    private var player = Player()

    init {
        volumeControl.label("Volume")
        root.setMinSize(300.0, 200.0)

        removeAllButton.setOnAction {
            songsList.items.clear()
            musicList.clear()
        }

        removeFileButton.setOnAction {
            var index = -1
            musicList.forEach {
                index++
                println("${it.source.takeLastWhile { (it != '\\' && it != '/') }.takeWhile { it != '.' }} == ${songsList.selectedItem?.text?.drop(3)}")
                if(it.source.takeLastWhile {
                            (it != '\\' && it != '/')
                        }.takeWhile { it != '.' }
                        == songsList.selectedItem?.text?.drop(3))
                    return@forEach
            }
            if(index > -1) {
                musicList.removeAt(index)
                songsList.items.remove(songsList.selectedItem)
                refreshList()
            }
        }

        addFolderButton.setOnAction {
            val folder = chooseDirectory("Select folder")
            println(folder?.name)
            folder?.list { dir, name ->
                try {   // safely add music files
                    val file = dir.toURI().toString() + name
                    println(file)
                    // add selected media to arraylist
                    // TODO: check for file extension (so you cannot add unsupported files)
                    musicList.add(Media(file))
                    refreshList()
                } catch (e : Exception){
                    println(e)
                }
                true
            }
        }

        addFileButton.setOnAction {
            val extensions = arrayOf(FileChooser.ExtensionFilter("MP3", "*.mp3"),FileChooser.ExtensionFilter("WAV", "*.wav"))
            // let the user choose his song
            val file = chooseFile("Select music file", extensions)
            // if he selected something
            if(file.isNotEmpty()) {
                // add selected media to arraylist
                musicList.add(Media(File(file.toString().drop(1).dropLast(1)).toURI().toString()))
                refreshList()
            }
        }

        startButton.setOnAction {
            // if nothing is playing, start playing something
            if (!player.playing) {
                player = Player()
                if (player.play(volumeControl.value / 100.0, musicList[0])) {
                    songsList.refresh()
                }
            }
        }
    }

    private fun refreshList(){
            //create temporary variable to store song number
            var musicNumber = 0

            // update current songs names list by removing every element in it...
            songsList.items.remove(0, songsList.items.size)
            var nameList = listOf<String>()
            // ...and creating new list
            musicList.forEach {
                nameList = nameList.plusElement(
                        it.source
                                .takeLastWhile {
                                    (it != '\\' && it != '/')
                                }
                                .takeWhile {
                                    it != '.'
                                }
                )
            }

            // then add to display list, giving them it's number
            nameList.forEach { name ->
                val item = Text("${musicNumber + 1}. $name")
                item.setOnMouseClicked {
                    // when user double click on song, start playing it
                    if (it.clickCount == 2) {
                        player.userChange = true
                        player.playing = false
                        player = Player()
                        player.play(volumeControl.value, musicList[nameList.indexOf(name)])
                        songsList.refresh()
                    }
                }
                songsList.items.add(item)
                musicNumber++
            }
    }

    inner class Player {
        private var paused = false
        var userChange = false
        var playing = false

        fun play(volume: Double, music: Media): Boolean {
            if (musicList.isNotEmpty()) {
                // lets not crash all music player (in case if something goes wrong)
                try {
                    var updateSoundProgress = true
                    val player = MediaPlayer(music)
                    player.volume = volume
                    player.onReady.run {
                        playing = true
                        player.play()
                        thread {
                            // set max song progress only once
                            var lock = false

                            // update song progress as long as it's playing current music, it's actually playing and window exists
                            while (playing && this@PlayerWindow.isDocked) {
                                runLater {
                                    if (!lock) {
                                        // check for availability of song time
                                        if (!player.stopTime.isUnknown) {
                                            songProgress.max = player.stopTime.toSeconds()
                                            lock = true
                                        }
                                    }
                                    // if user doesn't try to change song progress
                                    if (updateSoundProgress) {
                                        val time = SimpleDateFormat("mm:ss")
                                        time.timeZone = TimeZone.getTimeZone("GMT")
                                        currentTime.text = time.format(player.currentTime.toMillis())   // update current song time
                                        songProgress.value = player.currentTime.toSeconds()     // update song progress slider
                                    }
                                }
                                Thread.sleep(250)   // lets update once / (1/4)sec
                            }
                            try {
                                player.stop()   // it can cause null pointer exception while shutting down application
                                player.dispose()
                                runLater {
                                    currentSong.text = ""
                                    currentTime.text = "00:00"
                                    songProgress.value = 0.0
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }
                    if (paused)
                        player.pause()

                    player.setAudioSpectrumListener { _, _, magnitudes, _ ->
                        // audio visuals
                        val effects = arrayListOf(0f,0f,0f,0f,0f,0f,0f,0f)
                        var index = 0
                        for(i in 0 until magnitudes.size/2){
                            if(i != 0 && i % 8 == 0) {
                                effects[index] = effects[index] / 8
                                index++
                            }
                            effects[index] += magnitudes[i]
                        }
                        effects[index] = effects[index] / 8
                        musicVisual1.progress = ((effects[0].toDouble() + 60.0) / 25)
                        musicVisual2.progress = ((effects[1].toDouble() + 60.0) / 25)
                        musicVisual3.progress = ((effects[2].toDouble() + 60.0) / 25)
                        musicVisual4.progress = ((effects[3].toDouble() + 60.0) / 25)
                        musicVisual5.progress = ((effects[4].toDouble() + 60.0) / 25)
                        musicVisual6.progress = ((effects[5].toDouble() + 60.0) / 25)
                        musicVisual7.progress = ((effects[6].toDouble() + 60.0) / 25)
                        musicVisual8.progress = ((effects[7].toDouble() + 60.0) / 25)
                    }

                    currentSong.text = musicList[musicList.indexOf(music)] // display current song name
                            .source
                            .takeLastWhile {
                                (it != '\\' && it != '/')
                            }
                            .takeWhile {
                                it != '.'
                            }

                    player.setOnPlaying {
                        paused = false
                    }

                    player.setOnPaused {
                        paused = true
                    }

                    player.setOnEndOfMedia {
                        // play next music
                        playing = false
                        updateSoundProgress = false
                        player.stop()
                        next(music, player)
                    }

                    nextButton.setOnAction {
                        // play next music
                        playing = false
                        updateSoundProgress = false
                        player.stop()
                        next(music, player)
                    }

                    prevButton.setOnAction {
                        // play prev music
                        playing = false
                        updateSoundProgress = false
                        player.stop()
                        prev(music, player)
                    }

                    volumeControl.setOnScroll {
                        if (it.deltaY > 0)
                            volumeControl.increment()
                        else
                            volumeControl.decrement()
                        player.volume = volumeControl.value / 100.0
                    }

                    volumeControl.setOnMouseDragged {
                        player.volume = volumeControl.value / 100.0
                    }

                    startButton.setOnAction {
                        if (!playing) {
                            if (play(volumeControl.value / 100.0, musicList[0])) {
                                songsList.refresh()
                            }
                        }
                        else if (paused)
                            player.play()
                        else
                            player.pause()
                    }

                    stopButton.setOnAction {
                        // stop playing
                        player.stop()
                        player.seek(Duration(0.0))
                        currentSong.text = ""
                        val time = SimpleDateFormat("mm:ss")
                        time.timeZone = TimeZone.getTimeZone("GMT")
                        currentTime.text = "00:00"
                        songProgress.value = 0.0
                        playing = false
                        paused = false
                    }

                    songProgress.setOnMouseDragged {
                        if (updateSoundProgress)
                            updateSoundProgress = false
                        val time = SimpleDateFormat("mm:ss")
                        time.timeZone = TimeZone.getTimeZone("GMT")
                        currentTime.text = time.format(TimeUnit.SECONDS.toMillis(songProgress.value.toLong()))
                    }

                    songProgress.setOnMouseReleased {
                        player.seek(Duration.seconds(songProgress.value))
                        updateSoundProgress = true
                    }


                } catch (e: Exception) {
                    println(e.toString())
                }
                return true
            }
            return false
        }

        private fun next(music: Media, player: MediaPlayer) {
            // if there is next song, play it
            if (musicList.indexOf(music) + 1 >= musicList.size) {
                player.seek(Duration(0.0))
            } else {
                if (!userChange)
                    if (play(volumeControl.value / 100.0, musicList[musicList.indexOf(music) + 1])) {
                        songsList.refresh()
                    }
            }
        }

        private fun prev(music: Media, player: MediaPlayer) {
            // if there is prev song, play it
            if (musicList.indexOf(music) - 1 < 0) {
                player.seek(Duration(0.0))
            } else {
                if (!userChange)
                    if (play(volumeControl.value / 100.0, musicList[musicList.indexOf(music) - 1])) {
                        songsList.refresh()
                    }
            }
        }
    }
}
