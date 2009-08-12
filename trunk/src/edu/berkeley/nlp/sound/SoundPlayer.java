/**
 * 
 */
package edu.berkeley.nlp.sound;

/**
 * @author petrov
 *
 */
//Example 17-3. SoundPlayer.java


import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.sound.sampled.*;
import javax.sound.midi.*;

/**
 * This class is a Swing component that can load and play a sound clip,
 * displaying progress and controls.  The main( ) method is a test program.
 * This component can play sampled audio or MIDI files, but handles them 
 * differently. For sampled audio, time is reported in microseconds, tracked in
 * milliseconds and displayed in seconds and tenths of seconds. For midi
 * files time is reported, tracked, and displayed in MIDI "ticks".
 * This program does no transcoding, so it can only play sound files that use
 * the PCM encoding.
 */
public class SoundPlayer extends JComponent {
    boolean midi;            // Are we playing a midi file or a sampled one?
    Sequence sequence;       // The contents of a MIDI file
    Sequencer sequencer;     // We play MIDI Sequences with a Sequencer
    Clip clip;               // Contents of a sampled audio file
    boolean playing = false; // whether the sound is currently playing

    // Length and position of the sound are measured in milliseconds for 
    // sampled sounds and MIDI "ticks" for MIDI sounds
    int audioLength;         // Length of the sound.  
    int audioPosition = 0;   // Current position within the sound

    // The following fields are for the GUI
    JButton play;             // The Play/Stop button
    JSlider progress;         // Shows and sets current position in sound
    JLabel time;              // Displays audioPosition as a number
    Timer timer;              // Updates slider every 100 milliseconds

    // The main method just creates a SoundPlayer in a Frame and displays it
    public static void main(String[  ] args) 
        throws IOException,
               UnsupportedAudioFileException,
               LineUnavailableException,
               MidiUnavailableException,
               InvalidMidiDataException
    {
        SoundPlayer player;

        File file = new File("/Users/petrov/Desktop/M1F1-Alaw-AFsp.wav");   // This is the file we'll be playing
        // Determine whether it is midi or sampled audio
        boolean ismidi;
        try {
            // We discard the return value of this method; we just need to know
            // whether it returns successfully or throws an exception
            MidiSystem.getMidiFileFormat(file);
            ismidi = true;
        }
        catch(InvalidMidiDataException e) {
            ismidi = false;
        }

        // Create a SoundPlayer object to play the sound.
        player = new SoundPlayer(file, ismidi);

        // Put it in a window and play it
        JFrame f = new JFrame("SoundPlayer");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane( ).add(player, "Center");
        f.pack( );
        f.setVisible(true);
    }

    // Create a SoundPlayer component for the specified file.
    public SoundPlayer(File f, boolean isMidi)
        throws IOException,
               UnsupportedAudioFileException,
               LineUnavailableException,
               MidiUnavailableException,
               InvalidMidiDataException
    {
        if (isMidi) {     // The file is a MIDI file
            midi = true;
            // First, get a Sequencer to play sequences of MIDI events
            // That is, to send events to a Synthesizer at the right time.
            sequencer = MidiSystem.getSequencer( );  // Used to play sequences
            sequencer.open( );                       // Turn it on.

            // Get a Synthesizer for the Sequencer to send notes to
            Synthesizer synth = MidiSystem.getSynthesizer( );
            synth.open( );  // acquire whatever resources it needs
            
            // The Sequencer obtained above may be connected to a Synthesizer
            // by default, or it may not.  Therefore, we explicitly connect it.
            Transmitter transmitter = sequencer.getTransmitter( );
            Receiver receiver = synth.getReceiver( );
            transmitter.setReceiver(receiver);
            
            // Read the sequence from the file and tell the sequencer about it
            sequence = MidiSystem.getSequence(f);
            sequencer.setSequence(sequence);
            audioLength = (int)sequence.getTickLength( ); // Get sequence length
        }
        else {            // The file is sampled audio
            midi = false;
            // Getting a Clip object for a file of sampled audio data is kind
            // of cumbersome.  The following lines do what we need.
            AudioInputStream ain = AudioSystem.getAudioInputStream(f);
            try {
                DataLine.Info info =
                    new DataLine.Info(Clip.class,ain.getFormat( ));
                clip = (Clip) AudioSystem.getLine(info);
                clip.open(ain);
            }
            finally { // We're done with the input stream.
                ain.close( );
            }
            // Get the clip length in microseconds and convert to milliseconds
            audioLength = (int)(clip.getMicrosecondLength( )/1000);
        }

        // Now create the basic GUI
        play = new JButton("Play");                // Play/stop button
        progress = new JSlider(0, audioLength, 0); // Shows position in sound
        time = new JLabel("0");                    // Shows position as a #

        // When clicked, start or stop playing the sound
        play.addActionListener(new ActionListener( ) {
                public void actionPerformed(ActionEvent e) {
                    if (playing) stop( ); else play( );
                }
            });

        // Whenever the slider value changes, first update the time label.
        // Next, if we're not already at the new position, skip to it.
        progress.addChangeListener(new ChangeListener( ) {
                public void stateChanged(ChangeEvent e) {
                    int value = progress.getValue( );
                    // Update the time label
                    if (midi) time.setText(value + "");
                    else time.setText(value/1000 + "." +
                                      (value%1000)/100);
                    // If we're not already there, skip there.
                    if (value != audioPosition) skip(value);
                }
            });
        
        // This timer calls the tick( ) method 10 times a second to keep 
        // our slider in sync with the music.
        timer = new javax.swing.Timer(100, new ActionListener( ) {
                public void actionPerformed(ActionEvent e) { tick( ); }
            });
        
        // put those controls in a row
        Box row = Box.createHorizontalBox( );
        row.add(play);
        row.add(progress);
        row.add(time);
        
        // And add them to this component.
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.add(row);

        // Now add additional controls based on the type of the sound
        if (midi) addMidiControls( );
        else addSampledControls( );
    }

    /** Start playing the sound at the current position */
    public void play( ) {
        if (midi) sequencer.start( );
        else clip.start( );
        timer.start( );
        play.setText("Stop");
        playing = true;
    }

    /** Stop playing the sound, but retain the current position */
    public void stop( ) {
        timer.stop( );
        if (midi) sequencer.stop( );
        else clip.stop( );
        play.setText("Play");
        playing = false;
    }

    /** Stop playing the sound and reset the position to 0 */
    public void reset( ) {
        stop( );
        if (midi) sequencer.setTickPosition(0);
        else clip.setMicrosecondPosition(0);
        audioPosition = 0; 
        progress.setValue(0);
    }

    /** Skip to the specified position */
    public void skip(int position) { // Called when user drags the slider
        if (position < 0 || position > audioLength) return;
        audioPosition = position;
        if (midi) sequencer.setTickPosition(position);
        else clip.setMicrosecondPosition(position * 1000);
        progress.setValue(position); // in case skip( ) is called from outside
    }

    /** Return the length of the sound in ms or ticks */
    public int getLength( ) { return audioLength; }

    // An internal method that updates the progress bar.
    // The Timer object calls it 10 times a second.
    // If the sound has finished, it resets to the beginning
    void tick( ) {
        if (midi && sequencer.isRunning( )) {
            audioPosition = (int)sequencer.getTickPosition( );
            progress.setValue(audioPosition);
        }
        else if (!midi && clip.isActive( )) {
            audioPosition = (int)(clip.getMicrosecondPosition( )/1000);
            progress.setValue(audioPosition);
        }
        else reset( );  
    }

    // For sampled sounds, add sliders to control volume and balance
    void addSampledControls( ) {
        try {
            FloatControl gainControl =
                (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
            if (gainControl != null) this.add(createSlider(gainControl));
        }
        catch(IllegalArgumentException e) {
            // If MASTER_GAIN volume control is unsupported, just skip it
        }

        try {
            // FloatControl.Type.BALANCE is probably the correct control to
            // use here, but it doesn't work for me, so I use PAN instead.
            FloatControl panControl =
                (FloatControl)clip.getControl(FloatControl.Type.PAN);
            if (panControl != null) this.add(createSlider(panControl));
        }
        catch(IllegalArgumentException e) {  }
    }


    // Return a JSlider component to manipulate the supplied FloatControl
    // for sampled audio.
    JSlider createSlider(final FloatControl c) {
        if (c == null) return null;
        final JSlider s = new JSlider(0, 1000);
        final float min = c.getMinimum( );
        final float max = c.getMaximum( );
        final float width = max-min;
        float fval = c.getValue( );
        s.setValue((int) ((fval-min)/width * 1000));

        java.util.Hashtable labels = new java.util.Hashtable(3);
        labels.put(new Integer(0), new JLabel(c.getMinLabel( )));
        labels.put(new Integer(500), new JLabel(c.getMidLabel( )));
        labels.put(new Integer(1000), new JLabel(c.getMaxLabel( )));
        s.setLabelTable(labels);
        s.setPaintLabels(true);

        s.setBorder(new TitledBorder(c.getType( ).toString( ) + " " +
                                     c.getUnits( )));

        s.addChangeListener(new ChangeListener( ) {
                public void stateChanged(ChangeEvent e) {
                    int i = s.getValue( );
                    float f = min + (i*width/1000.0f);
                    c.setValue(f);
                }
            });
        return s;
    }

    // For Midi files, create a JSlider to control the tempo,
    // and create JCheckBoxes to mute or solo each MIDI track.
    void addMidiControls( ) {
        // Add a slider to control the tempo
        final JSlider tempo = new JSlider(50, 200);
        tempo.setValue((int)(sequencer.getTempoFactor( )*100));
        tempo.setBorder(new TitledBorder("Tempo Adjustment (%)"));
        java.util.Hashtable labels = new java.util.Hashtable( );
        labels.put(new Integer(50), new JLabel("50%"));
        labels.put(new Integer(100), new JLabel("100%"));
        labels.put(new Integer(200), new JLabel("200%"));
        tempo.setLabelTable(labels);
        tempo.setPaintLabels(true);
        // The event listener actually changes the tempo
        tempo.addChangeListener(new ChangeListener( ) {
                public void stateChanged(ChangeEvent e) {
                    sequencer.setTempoFactor(tempo.getValue( )/100.0f);
                }
            });

        this.add(tempo);

        // Create rows of solo and checkboxes for each track
        Track[  ] tracks = sequence.getTracks( );
        for(int i = 0; i <= tracks.length; i++) {
            final int tracknum = i;
            // Two checkboxes per track
            final JCheckBox solo = new JCheckBox("solo");
            final JCheckBox mute = new JCheckBox("mute");
            // The listeners solo or mute the track
            solo.addActionListener(new ActionListener( ) {
                    public void actionPerformed(ActionEvent e) {
                        sequencer.setTrackSolo(tracknum,solo.isSelected( ));
                    }
                });
            mute.addActionListener(new ActionListener( ) {
                    public void actionPerformed(ActionEvent e) {
                        sequencer.setTrackMute(tracknum,mute.isSelected( ));
                    }
                });

            // Build up a row
            Box box = Box.createHorizontalBox( );
            box.add(new JLabel("Track " + tracknum));
            box.add(Box.createHorizontalStrut(10));
            box.add(solo);
            box.add(Box.createHorizontalStrut(10));
            box.add(mute);
            box.add(Box.createHorizontalGlue( ));
            // And add it to this component
            this.add(box);
        }
    }
}
