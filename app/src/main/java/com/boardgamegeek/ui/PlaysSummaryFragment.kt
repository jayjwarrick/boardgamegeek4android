package com.boardgamegeek.ui

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.boardgamegeek.R
import com.boardgamegeek.auth.AccountUtils
import com.boardgamegeek.entities.*
import com.boardgamegeek.extensions.createSmallCircle
import com.boardgamegeek.extensions.getText
import com.boardgamegeek.extensions.setBggColors
import com.boardgamegeek.extensions.setColorViewValue
import com.boardgamegeek.pref.SyncPrefs
import com.boardgamegeek.ui.viewmodel.PlaysSummaryViewModel
import com.boardgamegeek.util.PreferencesUtils
import kotlinx.android.synthetic.main.fragment_plays_summary.*
import org.jetbrains.anko.support.v4.startActivity

class PlaysSummaryFragment : Fragment(), OnSharedPreferenceChangeListener {
    val viewModel by lazy {
        ViewModelProviders.of(this).get(PlaysSummaryViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_plays_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout.setBggColors()
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = viewModel.refresh()
        }

        syncButton.setOnClickListener {
            PreferencesUtils.setSyncPlays(context)
            viewModel.refresh()
            PreferencesUtils.setSyncPlaysTimestamp(context)
            bindSyncCard()
        }

        syncCancelButton.setOnClickListener {
            PreferencesUtils.setSyncPlaysTimestamp(context)
            bindSyncCard()
        }

        hIndexView.text = context?.getText(R.string.game_h_index_prefix, PreferencesUtils.getGameHIndex(context))
        morePlayStatsButton.setOnClickListener {
            startActivity<PlayStatsActivity>()
        }

        viewModel.plays.observe(this, Observer { swipeRefreshLayout.isRefreshing = (it.status == Status.REFRESHING) })
        viewModel.playsInProgress.observe(this, Observer { playEntities -> bindInProgressPlays(playEntities) })
        viewModel.playsNotInProgress.observe(this, Observer { playEntities -> bindRecentPlays(playEntities) })
        viewModel.playCount.observe(this, Observer { playCount -> bindPlayCount(playCount ?: 0) })
        viewModel.players.observe(this, Observer { playerEntities -> bindPlayers(playerEntities) })
        viewModel.locations.observe(this, Observer { locationEntities -> bindLocations(locationEntities) })
        viewModel.colors.observe(this, Observer { playerColorEntities -> bindColors(playerColorEntities) })
    }

    override fun onResume() {
        super.onResume()
        bindStatusMessage()
        bindSyncCard()
        SyncPrefs.getPrefs(requireContext()).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        SyncPrefs.getPrefs(requireContext()).unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        bindStatusMessage()
    }

    private fun bindStatusMessage() {
        val oldestDate = SyncPrefs.getPlaysOldestTimestamp(requireContext())
        val newestDate = SyncPrefs.getPlaysNewestTimestamp(requireContext())
        syncStatusView.text = when {
            oldestDate == Long.MAX_VALUE && newestDate == 0L -> getString(R.string.plays_sync_status_none)
            oldestDate == 0L -> String.format(getString(R.string.plays_sync_status_new), millisAsDate(newestDate))
            newestDate == 0L -> String.format(getString(R.string.plays_sync_status_old), millisAsDate(oldestDate))
            else -> String.format(getString(R.string.plays_sync_status_range), millisAsDate(oldestDate), millisAsDate(newestDate))
        }
    }

    private fun millisAsDate(millis: Long) = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE)

    private fun bindSyncCard() {
        syncCard.visibility = if (PreferencesUtils.getSyncPlays(context) || PreferencesUtils.getSyncPlaysTimestamp(context) > 0)
            View.GONE
        else
            View.VISIBLE
    }

    private fun bindInProgressPlays(plays: List<PlayEntity>?) {
        val numberOfPlaysInProgress = plays?.size ?: 0
        val visibility = if (numberOfPlaysInProgress == 0) View.GONE else View.VISIBLE
        playsInProgressSubtitle.visibility = visibility
        playsInProgressContainer.visibility = visibility
        recentPlaysSubtitle.visibility = visibility

        playsInProgressContainer.removeAllViews()
        if (numberOfPlaysInProgress > 0) {
            plays?.forEach {
                addPlayToContainer(it, playsInProgressContainer)
            }
            playsCard.isVisible = true
        }
    }

    private fun bindRecentPlays(plays: List<PlayEntity>?) {
        recentPlaysContainer.removeAllViews()
        if (plays != null && plays.isNotEmpty()) {
            plays.forEach { addPlayToContainer(it, recentPlaysContainer) }
            playsCard.isVisible = true
            recentPlaysContainer.isVisible = true
        }
    }

    private fun addPlayToContainer(play: PlayEntity, container: LinearLayout) {
        val view = createRow(container, play.gameName, play.describe(requireContext(), true))
        view.setOnClickListener {
            PlayActivity.start(context,
                    play.internalId,
                    play.gameId,
                    play.gameName,
                    play.thumbnailUrl,
                    play.imageUrl,
                    play.heroImageUrl)
        }
    }

    private fun bindPlayCount(playCount: Int) {
        morePlaysButton.isVisible = true
        morePlaysButton.setText(R.string.more)
        val morePlaysCount = playCount - 5
        if (morePlaysCount > 0) {
            morePlaysButton.text = String.format(getString(R.string.more_suffix), morePlaysCount)
        }
        morePlaysButton.setOnClickListener { startActivity<PlaysActivity>() }
    }

    private fun bindPlayers(players: List<PlayerEntity>?) {
        playersContainer.removeAllViews()
        if (players == null || players.isEmpty()) {
            playersCard.isGone = true
            morePlayersButton.isGone = true
        } else {
            playersCard.isVisible = true
            for (player in players) {
                createRowWithPlayCount(playersContainer, player.description, player.playCount).apply {
                    setOnClickListener { BuddyActivity.start(requireContext(), player.username, player.name) }
                }
            }
            morePlayersButton.isVisible = true
        }
        morePlayersButton.setOnClickListener { startActivity<PlayersActivity>() }
    }

    private fun bindLocations(locations: List<LocationEntity>?) {
        locationsContainer.removeAllViews()
        if (locations == null || locations.isEmpty()) {
            locationsCard.isGone = true
            moreLocationsButton.isGone = true
        } else {
            locationsCard.isVisible = true
            for ((name, playCount) in locations) {
                createRowWithPlayCount(locationsContainer, name, playCount).apply {
                    setOnClickListener { LocationActivity.start(context, name) }
                }
            }
            moreLocationsButton.isVisible = true
        }
        moreLocationsButton.setOnClickListener { startActivity<LocationsActivity>() }
    }

    private fun createRow(container: ViewGroup, title: String, text: String): View {
        return LayoutInflater.from(context).inflate(R.layout.row_play_summary, container, false).apply {
            findViewById<TextView>(R.id.line1).text = title
            findViewById<TextView>(R.id.line2).text = text
            container.addView(this)
        }
    }

    private fun createRowWithPlayCount(container: LinearLayout, title: String, playCount: Int): View {
        return createRow(container, title, resources.getQuantityString(R.plurals.plays_suffix, playCount, playCount))
    }

    private fun bindColors(colors: List<PlayerColorEntity>?) {
        colorsContainer.removeAllViews()
        if (colors == null || colors.isEmpty()) {
            colorsCard.isGone = true
        } else {
            colorsCard.isVisible = true
            colors.forEach {
                colorsContainer.addView(requireContext().createSmallCircle().apply {
                    setColorViewValue(it.rgb)
                })
            }
        }
        editColorsButton.setOnClickListener {
            PlayerColorsActivity.start(context, AccountUtils.getUsername(context), null)
        }
    }
}
