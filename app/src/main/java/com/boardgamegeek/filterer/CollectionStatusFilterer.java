package com.boardgamegeek.filterer;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;

import com.boardgamegeek.R;

import java.util.ArrayList;
import java.util.List;

public class CollectionStatusFilterer extends CollectionFilterer {
	private boolean[] selectedStatuses;
	private boolean shouldJoinWithOr;

	public CollectionStatusFilterer() {
		setType(CollectionFilterDataFactory.TYPE_COLLECTION_STATUS);
	}

	public CollectionStatusFilterer(@NonNull Context context, @NonNull String data) {
		String[] d = data.split(DELIMITER);
		shouldJoinWithOr = (d[0].equals("1"));
		selectedStatuses = new boolean[d.length - 1];
		for (int i = 0; i < d.length - 1; i++) {
			selectedStatuses[i] = (d[i + 1].equals("1"));
		}
		init(context);
	}

	public CollectionStatusFilterer(@NonNull Context context, boolean[] selectedStatuses, boolean shouldJoinWithOr) {
		this.selectedStatuses = selectedStatuses;
		this.shouldJoinWithOr = shouldJoinWithOr;
		init(context);
	}

	private void init(@NonNull Context context) {
		setType(CollectionFilterDataFactory.TYPE_COLLECTION_STATUS);
		createDisplayText(context.getResources());
		createSelection(context.getResources());
	}

	private void createDisplayText(@NonNull Resources r) {
		String[] entries = r.getStringArray(R.array.collection_status_filter_entries);
		String displayText = "";

		for (int i = 0; i < selectedStatuses.length; i++) {
			if (selectedStatuses[i]) {
				if (displayText.length() > 0) {
					displayText += " " + (shouldJoinWithOr ? "|" : "&") + " ";
				}
				displayText += entries[i];
			}
		}

		displayText(displayText);
	}

	private void createSelection(@NonNull Resources r) {
		String[] values = r.getStringArray(R.array.collection_status_filter_values);
		String selection = "";
		List<String> selectionArgs = new ArrayList<>(selectedStatuses.length);

		for (int i = 0; i < selectedStatuses.length; i++) {
			if (selectedStatuses[i]) {
				if (selection.length() > 0) {
					selection += " " + (shouldJoinWithOr ? "OR" : "AND") + " ";
				}
				selection += values[i] + "=?";
				selectionArgs.add("1");
			}
		}
		selection(selection);
		selectionArgs(selectionArgs.toArray(new String[selectionArgs.size()]));
	}

	public boolean[] getSelectedStatuses() {
		return selectedStatuses;
	}

	@NonNull
	@Override
	public String flatten() {
		String s = (shouldJoinWithOr ? "1" : "0");
		for (boolean selected : selectedStatuses) {
			if (s.length() > 0) {
				s += DELIMITER;
			}
			s += (selected ? "1" : "0");
		}
		return s;
	}
}
