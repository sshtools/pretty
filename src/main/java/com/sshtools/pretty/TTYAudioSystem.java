package com.sshtools.pretty;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.unitgen.LineOut;
import com.jsyn.unitgen.SawtoothOscillator;
import com.sshtools.terminal.vt.javafx.JavaFXAudioSystem;

public class TTYAudioSystem extends JavaFXAudioSystem {
	private static final int MAX_NOTES = 16;

	static Logger LOG = LoggerFactory.getLogger(TTYAudioSystem.class);

	private TTY tty;
	private Synthesizer synth;
	private SawtoothOscillator osc;

	private LineOut lineOut;
	private ExecutorService executor;

	private LinkedBlockingDeque<Runnable> queue;

	public TTYAudioSystem(TTY tty) {
		this.tty = tty;
	}

	@Override
	protected void playBeep() {
		var aw = (PrettyAppWindow) tty.ttyContext().appWindow();
		aw.animateBell();
		if (!isMuted()) {
			super.playBeep();
		}
	}

	protected boolean isMuted() {
		return tty.ttyContext().getContainer().getConfiguration().getBoolean(Constants.MUTE_KEY,
				Constants.UI_SECTION);
	}

	@Override
	public void close() {
		if(executor != null) {
			executor.shutdown();
		}
	}

	@Override
	public void playNote(Note note) {
		checkQueue();
		while(true) {
			try {
				executor.execute(() -> {
					checkSynth();
					
					if(LOG.isDebugEnabled())
						LOG.info("Play note {}", note);
					
					osc.start();
					osc.amplitude.set(isMuted() ? 0 : note.vol());
					osc.frequency.set(note.freq(), note.dur());
					try {
						synth.sleepFor(note.dur());
					} catch (InterruptedException e) {
						throw new IllegalStateException("Interrupted.");
					} finally {
						lineOut.stop();
					}	
				});
				return;
			}
			catch(RejectedExecutionException ree) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					throw new IllegalStateException("Interrupted.");
				}
			}
		}
	}
	
	private void checkQueue() {
		if(queue == null) {
			queue = new LinkedBlockingDeque<Runnable>(MAX_NOTES);
			executor = new ThreadPoolExecutor(1, 1, Integer.MAX_VALUE,
				    TimeUnit.SECONDS, queue,
				    new ThreadPoolExecutor.AbortPolicy());
		}
	}

	private void checkSynth() {
		if (synth == null) {
			
			synth = JSyn.createSynthesizer();

			osc = new SawtoothOscillator();
			synth.add(osc);
			// synth.add( ugen = new SineOscillator() );
			// synth.add( ugen = new SubtractiveSynthVoice() );
			// Add an output mixer.
			lineOut = new LineOut();
			synth.add(lineOut);

			// Connect the oscillator to the left and right audio output.
			osc.getOutput().connect(0, lineOut.input, 0);
			osc.getOutput().connect(0, lineOut.input, 1);

			// Start synthesizer using default stereo output at 44100 Hz.
			synth.start();

	        // We only need to start the LineOut. It will pull data from the
	        // oscillator.
		}
        lineOut.start();
	}
}
