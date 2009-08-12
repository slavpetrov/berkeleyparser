/**
 * 
 */
package edu.berkeley.nlp.sound;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.sound.sampled.*;

/**
 * @author petrov
 *
 */
public class PlaySound extends JFrame {
	/*File AudioCapture01.java
	 This program demonstrates the capture
	 and subsequent playback of audio data.

	 A GUI appears on the screen containing
	 the following buttons:
	 Capture
	 Stop
	 Playback

	 Input data from a microphone is
	 captured and saved in a
	 ByteArrayOutputStream object when the
	 user clicks the Capture button.

	 Data capture stops when the user clicks
	 the Stop button.

	 Playback begins when the user clicks
	 the Playback button.

	 Tested using SDK 1.4.0 under Win2000
	 **************************************/

	boolean stopCapture = false;

	ByteArrayOutputStream byteArrayOutputStream;

	AudioFormat audioFormat;

	TargetDataLine targetDataLine;

	AudioInputStream audioInputStream;

	SourceDataLine sourceDataLine;

	public static void main(String args[]) throws java.net.MalformedURLException {

//		      java.applet.AudioClip clip =
//		          java.applet.Applet.newAudioClip(new java.net.URL("file:///Users/petrov/Data/timit/TIMIT/sa1a.wav"));//Desktop/M1F1-Alaw-AFsp.wav"));//
//		      clip.play( );
		      //
		//  
		new PlaySound();
	}//end main

	public PlaySound() {//constructor
		final JButton captureBtn = new JButton("Capture");
		final JButton stopBtn = new JButton("Stop");
		final JButton playBtn = new JButton("Playback");
		final JButton saveBtn = new JButton("Save");

		captureBtn.setEnabled(true);
		stopBtn.setEnabled(false);
		playBtn.setEnabled(false);
		saveBtn.setEnabled(false);

		//Register anonymous listeners
		captureBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				captureBtn.setEnabled(false);
				stopBtn.setEnabled(true);
				playBtn.setEnabled(false);
				saveBtn.setEnabled(false);
				//Capture input data from the microphone until the Stop button is clicked.
				captureAudio();
			}//end actionPerformed
		}//end ActionListener
				);//end addActionListener()
		getContentPane().add(captureBtn);

		stopBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				captureBtn.setEnabled(true);
				stopBtn.setEnabled(false);
				playBtn.setEnabled(true);
				saveBtn.setEnabled(true);
				//Terminate the capturing of input data from the microphone.
				stopCapture = true;
			}//end actionPerformed
		}//end ActionListener
				);//end addActionListener()
		getContentPane().add(stopBtn);

		playBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Play back all of the data that was saved during capture.
				playAudio();
			}//end actionPerformed
		}//end ActionListener
				);//end addActionListener()
		getContentPane().add(playBtn);

		saveBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Play back all of the data that was saved during capture.
				saveAudio();
			}//end actionPerformed
		}//end ActionListener
				);//end addActionListener()
		getContentPane().add(saveBtn);

		getContentPane().setLayout(new FlowLayout());
		setTitle("Capture/Playback Demo");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(330, 70);
		setVisible(true);
	}//end constructor

	//This method captures audio input
	// from a microphone and saves it in
	// a ByteArrayOutputStream object.
	private void captureAudio() {
		try {
			//Get everything set up for
			// capture
			audioFormat = getAudioFormat();
			DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class,
					audioFormat);
			targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
			targetDataLine.open(audioFormat);
			targetDataLine.start();

			//Create a thread to capture the
			// microphone data and start it
			// running.  It will run until
			// the Stop button is clicked.
			Thread captureThread = new Thread(new CaptureThread());
			captureThread.start();
		} catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}//end catch
	}//end captureAudio method

	//This method plays back the audio
	// data that has been saved in the
	// ByteArrayOutputStream
	private void playAudio() {
		try {
			//Get everything set up for
			// playback.
			//Get the previously-saved data
			// into a byte array object.
			byte audioData[] = byteArrayOutputStream.toByteArray();
			//Get an input stream on the
			// byte array containing the data
			InputStream byteArrayInputStream = new ByteArrayInputStream(audioData);
			AudioFormat audioFormat = getAudioFormat();
			audioInputStream = new AudioInputStream(byteArrayInputStream,
					audioFormat, audioData.length / audioFormat.getFrameSize());
			DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class,
					audioFormat);
			sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
			sourceDataLine.open(audioFormat);
			sourceDataLine.start();

			//Create a thread to play back
			// the data and start it
			// running.  It will run until
			// all the data has been played
			// back.
			Thread playThread = new Thread(new PlayThread());
			playThread.start();
		} catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}//end catch
	}//end playAudio

	//This method plays back the audio
	// data that has been saved in the
	// ByteArrayOutputStream
	private void saveAudio() {
		try {
			//Get everything set up for
			// playback.
			//Get the previously-saved data
			// into a byte array object.
			byte audioData[] = byteArrayOutputStream.toByteArray();
			//Get an input stream on the
			// byte array containing the data
			InputStream byteArrayInputStream = new ByteArrayInputStream(audioData);
			AudioFormat audioFormat = getAudioFormat();
			audioInputStream = new AudioInputStream(byteArrayInputStream,
					audioFormat, audioData.length / audioFormat.getFrameSize());
//			DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class,
//					audioFormat);
//			sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
//			sourceDataLine.open(audioFormat);
//			sourceDataLine.start();
			// Create the output file
			File outputFile = saveFile(new File("MicRecording.wav"));

			AudioFileFormat.Type targetFileFormatType = AudioFileFormat.Type.WAVE;
			AudioSystem.write(audioInputStream, targetFileFormatType, outputFile);
			
//			outputFile.
//			//Create a thread to play back
//			// the data and start it
//			// running.  It will run until
//			// all the data has been played
//			// back.
//			Thread playThread = new Thread(new PlayThread());
//			playThread.start();
		} catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}//end catch
	}//end saveAudio
	
	
	/**
   * Use a JFileChooser in Open mode to select files
   * to open. Use a filter for FileFilter subclass to select
   * for *.java files. If a file is selected then read the
   * file and place the string into the textarea.
  **/
  File openFile () {
	 	File fFile = null;
	 
     JFileChooser fc = new JFileChooser ();
     fc.setDialogTitle ("Open File");

     // Choose only files, not directories
     fc.setFileSelectionMode ( JFileChooser.FILES_ONLY);

     // Start in current directory
     fc.setCurrentDirectory (new File ("."));

     // Set filter for Java source files.
//     fc.setFileFilter (fJavaFilter);

     // Now open chooser
     int result = fc.showOpenDialog (this);

     if (result == JFileChooser.CANCEL_OPTION) {
//         return true;
     } else if (result == JFileChooser.APPROVE_OPTION) {

         fFile = fc.getSelectedFile ();
         // Invoke the readFile method in this class
//         String file_string = readFile (fFile);

//         if (file_string != null)
//             fTextArea.setText (file_string);
//         else
//             return false;
//     } else {
//         return false;
     }
     return fFile;
  } // openFile


 /**
   * Use a JFileChooser in Save mode to select files
   * to open. Use a filter for FileFilter subclass to select
   * for "*.java" files. If a file is selected, then write the
   * the string from the textarea into it.
  **/
  File saveFile (File fFile) {
    File file = null;
    JFileChooser fc = new JFileChooser ();

    // Start in current directory
    fc.setCurrentDirectory (new File ("."));

    // Set filter for Java source files.
//    fc.setFileFilter (fJavaFilter);

    // Set to a default name for save.
    fc.setSelectedFile (fFile);

    // Open chooser dialog
    int result = fc.showSaveDialog (this);

    if (result == JFileChooser.CANCEL_OPTION) {
        return fFile;
    } else if (result == JFileChooser.APPROVE_OPTION) {
        file = fc.getSelectedFile ();
        if (fFile.exists ()) {
            int response = JOptionPane.showConfirmDialog (null,
              "Overwrite existing file?","Confirm Overwrite",
               JOptionPane.OK_CANCEL_OPTION,
               JOptionPane.QUESTION_MESSAGE);
            if (response == JOptionPane.CANCEL_OPTION) return fFile;
        }
        return file;
//        return writeFile (fFile, fTextArea.getText ());
    } else {
      return fFile;
    }
 } // saveFile

	
	
	
	
	
	
	
	
	
	
	//This method creates and returns an
	// AudioFormat object for a given set
	// of format parameters.  If these
	// parameters don't work well for
	// you, try some of the other
	// allowable parameter values, which
	// are shown in comments following
	// the declarations.
	private AudioFormat getAudioFormat() {

		//  	AudioFormat[] possible = getTargetFormats(new AudioFormat.Encoding("PCM_SIGNED"), AudioFormat sourceFormat) 

		float sampleRate = 44100;//8000.0F;//
		//8000,11025,16000,22050,44100
		int sampleSizeInBits = 16;
		//8,16
		int channels = 1;
		//1,2
		boolean signed = true;//false;// 
		//true,false
		boolean bigEndian = false;//true;//
		//true,false
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed,
				bigEndian);
	}//end getAudioFormat
	//	===================================//

	//	Inner class to capture data from
	//	 microphone
	class CaptureThread extends Thread {
		//An arbitrary-size temporary holding
		// buffer
		byte tempBuffer[] = new byte[10000];

		public void run() {
			byteArrayOutputStream = new ByteArrayOutputStream();
			stopCapture = false;
			try {//Loop until stopCapture is set
				// by another thread that
				// services the Stop button.
				while (!stopCapture) {
					//Read data from the internal
					// buffer of the data line.
					int cnt = targetDataLine.read(tempBuffer, 0, tempBuffer.length);
					if (cnt > 0) {
						//Save data in output stream
						// object.
						byteArrayOutputStream.write(tempBuffer, 0, cnt);
					}//end if
				}//end while
				byteArrayOutputStream.close();
			} catch (Exception e) {
				System.out.println(e);
				System.exit(0);
			}//end catch
		}//end run
	}//end inner class CaptureThread
	//	===================================//
	//	Inner class to play back the data
	//	 that was saved.

	class PlayThread extends Thread {
		byte tempBuffer[] = new byte[10000];

		public void run() {
			try {
				int cnt;
				//Keep looping until the input
				// read method returns -1 for
				// empty stream.
				while ((cnt = audioInputStream.read(tempBuffer, 0, tempBuffer.length)) != -1) {
					if (cnt > 0) {
						//Write data to the internal
						// buffer of the data line
						// where it will be delivered
						// to the speaker.
						sourceDataLine.write(tempBuffer, 0, cnt);
					}//end if
				}//end while
				//Block and wait for internal
				// buffer of the data line to
				// empty.
				sourceDataLine.drain();
				sourceDataLine.close();
			} catch (Exception e) {
				System.out.println(e);
				System.exit(0);
			}//end catch
		}//end run
	}//end inner class PlayThread
	//	===================================//

}
