/*
	Copyright (C) 2013-2015, The Aikuma Project
	AUTHORS: Oliver Adams and Florian Hanke
*/
package org.lp20.aikuma.audio;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import java.io.IOException;
import java.util.Iterator;
import org.lp20.aikuma.model.Recording;
import org.lp20.aikuma.model.Segments;
import org.lp20.aikuma.model.Segments.Segment;
import org.lp20.aikuma2.R;

/**
 * A player that plays both the original recording and its respeaking in an
 * interleaved fashion, whereby a segment of the original is played, followed
 * by the corresponding segment of the respeaking, followed by the next
 * original segment and so on.
 *
 * @author	Oliver Adams	<oliver.adams@gmail.com>
 * @author	Florian Hanke	<florian.hanke@gmail.com>
 */
public class InterleavedPlayer extends Player {
	
	/**
	 * Creates an InterleavedPlayer to play the supplied recording and it's
	 * original
	 *
	 * @param	context		The android-context creating this player for beep-audio
	 * @param	recording	The metadata of the recording to play.
	 * @throws	IOException	If there is an issue reading the recordings.
	 */
	public InterleavedPlayer(Context context, Recording recording) throws IOException {
		setRecording(recording);
		if (recording.isOriginal()) {
			throw new IllegalArgumentException("The supplied Recording is " +
					"not a respeaking. Use SimplePlayer instead.");
		}
		// Initialize original recording player
		Recording originalRecording = recording.getOriginal();
		setSampleRate(originalRecording.getSampleRate());
		original = new MarkedPlayer(
				originalRecording,
				new OriginalMarkerReachedListener(), true);
		
		// Initialize segment/derivative player
		if(context != null && recording.getFileType().equals(Recording.SEGMENT_TYPE)) {	//segment
			beeper = new Beeper(context, new MediaPlayer.OnCompletionListener() {
				@Override
				public void onCompletion(MediaPlayer mp) {
					advanceOriginalSegment();
					playOriginal();
					if(currentOriginalSegment.getEndSample() == Long.MAX_VALUE)
						completedOnce = true;
				}
			});
		} else {	//derivative(respeak/interpret)
			respeaking = new MarkedPlayer(recording,
					new RespeakingMarkerReachedListener(), true);
		}
		segments = new Segments(recording);
		initializeCompletionListeners();
		completedOnce = false;
	}

	// Initializes the completion listeners for the original and respeaking,
	// such that when one of them completes the InterleavedPlayer's
	// onCompletionListener is run, only once.
	// ex. Segment: Segment -> onCompletion
	// 	   Derivative: Original(completedOnce) -> Derivative -> onCompletion
	private void initializeCompletionListeners() {
		Player.OnCompletionListener bothCompletedListener = new 
				Player.OnCompletionListener() {
			//boolean completedOnce = false;
			@Override
			public void onCompletion(Player p) {
				if (respeaking == null || completedOnce) {
					if (onCompletionListener != null) {
						onCompletionListener.onCompletion(p);
					}
					reset();
					completedOnce = false;
				} else {
					completedOnce = true;
				}
			}
		};
		original.setOnCompletionListener(bothCompletedListener);
		if(respeaking != null)
			respeaking.setOnCompletionListener(bothCompletedListener);
	}

	/**
	 * Resumes playing the interleaved recording, from the original segment
	 * where it was last paused.
	 */
	public void play() {
		playOriginal();
	}

	/**
	 * Resets the player to the beginning.
	 */
	public void reset() {
		originalSegmentIterator = null;
		currentOriginalSegment = null;
		original.seekToSample(0l);
		if(respeaking != null)
			respeaking.seekToSample(0l);
	}

	/**
	 * Indicates whether the recording is currently being played.
	 *
	 * @return	true if the recording is currently playing; false otherwise.
	 */
	public boolean isPlaying() {
		return original.isPlaying() || (respeaking != null && respeaking.isPlaying());
	}

	/** Pauses the playback. */
	public void pause() {
		if (original.isPlaying()) {
			original.pause();
		}
		if (respeaking != null && respeaking.isPlaying()) {
			respeaking.pause();
		}
	}

	/**
	 * Get current point in the recording in milliseconds.
	 *
	 * @return	The current point in the recording in milliseconds as an int.
	 */
	public int getCurrentMsec() {
		return original.getCurrentMsec();
	}

	/**
	 * Get the duration of the recording in milliseconds.
	 *
	 * @return	The duration of the recording in milliseconds as an int.
	 */
	public int getDurationMsec() {
		return original.getDurationMsec();
	}

	/** Releases resources associated with the InterleavedPlayer. */
	public void release() {
		original.release();
		if(respeaking != null)
			respeaking.release();
	}

	// Plays the current original segment.
	private void playOriginal() {
		playSegment(getCurrentOriginalSegment(), original);
	}

	private Segment getCurrentOriginalSegment() {
		if (currentOriginalSegment == null) {
			advanceOriginalSegment();
		}
		return currentOriginalSegment;
	}

	// Plays the current respeaking segment.
	private void playRespeaking() {
		if(respeaking != null)
			playSegment(getCurrentRespeakingSegment(), respeaking);
	}

	private Segment getCurrentRespeakingSegment() {
		return segments.getRespeakingSegment(getCurrentOriginalSegment());
	}

	// Plays the specified segment in the specified player.
	private void playSegment(Segment segment, MarkedPlayer player) {
		if (segment != null) {
			player.seekTo(segment);
			player.setNotificationMarkerPosition(segment);
			player.play();
		}
	}

	// Moves forward to the next original segment in the recording.
	private void advanceOriginalSegment() {
		if (getOriginalSegmentIterator().hasNext()) {
			currentOriginalSegment = getOriginalSegmentIterator().next();
		} else {
			long startSample;
			if (currentOriginalSegment != null) {
				startSample = currentOriginalSegment.getEndSample();
			} else {
				startSample = 0l;
			}
			currentOriginalSegment = new Segment(startSample, Long.MAX_VALUE);
		}
	 }

	// Gets an iterator over the segments of the original recording.
	private Iterator<Segment> getOriginalSegmentIterator() {
		if (originalSegmentIterator == null) {
			originalSegmentIterator = segments.getOriginalSegmentIterator();
		}
		return originalSegmentIterator;
	}

	private MarkedPlayer original;
	private MarkedPlayer respeaking;
	private Segments segments;
	private Segment currentOriginalSegment;
	private Iterator<Segment> originalSegmentIterator;
	private Player.OnCompletionListener onCompletionListener;
	private Recording recording;
	private Beeper beeper;

	private void setRecording(Recording recording) {
		this.recording = recording;
	}

	public Recording getRecording() {
		return this.recording;
	}

	/**
	 * Implements a method for the original audio source to call when an
	 * original segment has finished playing.
	 */
	private class OriginalMarkerReachedListener extends
			MarkedPlayer.OnMarkerReachedListener {
		public void onMarkerReached(MarkedPlayer p) {
			Log.i("release", "original onMarker reached, completedOnce = " +
					completedOnce);
			original.pause();
			if(respeaking != null)
				playRespeaking();
			else {
				if (!completedOnce) {
					if(beeper != null)
						beeper.beep();
					else {
						advanceOriginalSegment();
						playOriginal();
						if(currentOriginalSegment.getEndSample() == Long.MAX_VALUE)
							completedOnce = true;
					}
				}
			}
		}
	}

	/**
	 * Implements a method for the respeaking audio source to call when a
	 * respeaking segment has finished playing.
	 */
	private class RespeakingMarkerReachedListener extends
			MarkedPlayer.OnMarkerReachedListener {
		public void onMarkerReached(MarkedPlayer p) {
			Log.i("release", "respeaking onMarker reached, completedOnce = " +
					completedOnce);
			respeaking.pause();
			if (!completedOnce) {
				advanceOriginalSegment();
				playOriginal();
			}
		}
	}

	/**
	 * Gets the sample rate of this player.
	 *
	 * @return	The sample rate of the player as a long.
	 */
	public long getSampleRate() {
		//If the sample rate is less than zero, then this indicates that there
		//wasn't a sample rate found in the metadata file.
		if (sampleRate <= 0l) {
			throw new RuntimeException(
					"The sampleRate of the recording is not known.");
		}
		return sampleRate;
	}

	private void setSampleRate(long sampleRate) {
		this.sampleRate = sampleRate;
	}

	/**
	 * Converts samples to milliseconds assuming this players sample rate
	 *
	 * @param	sample	A sample value to be converted
	 * @return	The value in milliseconds as an int.
	 */
	public int sampleToMsec(long sample) {
		long msec = sample / (getSampleRate() / 1000);
		if (msec > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int) msec;
	}

	/**
	 * Seek to a given point in the recording in milliseconds.
	 *
	 * @param	msec	The point in the recording to seek to in milliseconds.
	 */
	public void seekToMsec(int msec) {
		reset();
		while (getOriginalSegmentIterator().hasNext()) {
			currentOriginalSegment = getOriginalSegmentIterator().next();
			int start = sampleToMsec(currentOriginalSegment.getStartSample());
			int end = sampleToMsec(currentOriginalSegment.getEndSample());
			//Log.i("interleaved", msec + " / " + this.getDurationMsec() + ": (" + start + ", " + end + ")");
			if (msec >= start && msec <= end)
				break;			
		}
	}

	/**
	 * Set the callback to be run when the recording completes playing.
	 *
	 * @param	listener	The callback to call when playing is complete.
	 */
	public void setOnCompletionListener(
			final Player.OnCompletionListener listener) {
		this.onCompletionListener = listener;
	}

	private static boolean completedOnce = false;
	private long sampleRate;
}
