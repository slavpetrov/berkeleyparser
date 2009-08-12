/**
 * 
 */
package edu.berkeley.nlp.sound;

/**
 * @author petrov
 *
 */
import java.io.*;
import javax.sound.sampled.*;
import javax.sound.sampled.DataLine.*;
 
class Play
{
	public static boolean debug = false;
 
	// the main startup function
	public static void main(String[] args)
	{
		int m = 0;
		float start=0;
		float end=0;
 
		if (0 == args.length)
		{
			System.out.println("Play filename quiettime ...");
			return;
		}
 
		for (int i = 0; i< args.length; i++)
		{
			if(args[i].equals("-s"))m = 1;
			else if(args[i].equals("-e"))m = 2;
			else if(args[i].equals("-d"))
			{
				System.out.println("debug set");
				debug = true;
			}
			else
			{
				System.out.println("arg="+args[i]);
				try
				{
				float s = Float.parseFloat(args[i]);	// attempt to make an integer
 
				switch(m)
				{
					case 0:
						try{Thread.sleep((int)(1000*s));}catch(Exception e){}		// sleep as long as requested
						break;
					case 1:
						start = s;
						break;
					case 2:
						end = s;
						break;
				}
				}
				catch(NumberFormatException e)			// not a number, must be a file name
				{
				new Sound(args[i]).play(start,end);			// try to play it
				start = 0;
				end = 0;
				}
			}
		}
		System.exit(0);		// shut down the JVM to stop the hidden Sound threads
	}
}
 
class Sound implements LineListener
{
	private float bufTime;
	private File soundFile;
 
	private SourceDataLine line;
	private AudioInputStream stream;
	private AudioFormat format;
	private Info info;
	private boolean opened;
	private int frameSize;
	private long frames;
	private int bufFrames;
	private int bufsize;
	private boolean running;
	private boolean shutdown;
	private long firstFrame, lastFrame;
	private float frameRate;
	private long currentFrame;
	private float currentTime;
	private Thread playThread;
 
	// constructor, take a path to an audio file
	public Sound(String path)
	{
		this(path, 2);	// use 2 second buffer
	}
 
 
	// or a path and a buffer size
	public Sound(String path, float bt)
	{
		this(new File(path),bt);
	}
 
	public Sound(File sf, float bt)
	{
		bufTime = bt;		// seconds per buffer
		soundFile = sf;
		openSound();
	}
 
	private void openSound()
	{
		System.out.println("Opening file"+soundFile.getName());
		try
		{
			firstFrame = 0;
			currentFrame = 0;
			shutdown = false;
			running = false;
			stream=AudioSystem.getAudioInputStream(soundFile);
			format=stream.getFormat();
			if(format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED)
			{
				System.out.println("Converting Audio stream format");
				stream = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED,stream);
				format = stream.getFormat();
			}
 
			frameSize = format.getFrameSize();
			frames = stream.getFrameLength();
			lastFrame = frames;
			frameRate = format.getFrameRate();
 
			bufFrames = (int)(frameRate*bufTime);
			bufsize = frameSize * bufFrames;
 
			System.out.println("frameRate="+frameRate);
 
			System.out.println("frames="+frames+" frameSize="+frameSize+" bufFrames="+bufFrames+" bufsize="+bufsize);
 
			info=new Info(SourceDataLine.class,format,bufsize);
			line = (SourceDataLine)AudioSystem.getLine(info);
			line.addLineListener(this);
			line.open(format,bufsize);
			opened = true;
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
 
	public void stop()
	{
		System.out.println ("Stopping");
		if(running)
		{
			running = false;
			shutdown = true;
			if(playThread != null)
			{
				playThread.interrupt();
				try{playThread.join();}
				catch(Exception e){e.printStackTrace();}
			}
		}
 
		if (opened) close();
	}
 
	private void close()
	{
		System.out.println("close line");
		line.close();
		try{stream.close();}catch(Exception e){}
		line.removeLineListener(this);
		opened = false;
	}
 
 
	// set the start and stop time
	public void setTimes(float t0, float tz)
	{
		currentTime = t0;
		firstFrame = (long)(frameRate*t0);
		if(tz > 0)
			lastFrame = (long)(frameRate*tz);
		else lastFrame = frames;
		if(lastFrame > frames)lastFrame = frames;
		if(firstFrame > lastFrame)firstFrame = lastFrame;
	}
 
	public void playAsync(float start, float end)
	{
		setTimes(start,end);
		playAsync();
	}
 
	// play the sound asynchronously */
	public void playAsync()
	{
		System.out.println("Play async");
		if(!opened)openSound();
 
		if(opened && !running)
		{
			playThread = new Thread(new Runnable(){public void run(){play();}});
			playThread.start();
		}
	}
 
 
	public void play(float start, float end)
	{
		setTimes(start,end);
		play();
	}
 
	// play the sound in the calling thread
	public void play()
	{
		if(!opened)openSound();
 
 
		if(opened && !running)
		{
 
 
			running = true;
			int writeSize = frameSize*bufFrames/2; // amount we write at a time
 
 
			byte buf[] = new byte[writeSize];	// our io buffer
 
			int len;
			long framesRemaining = lastFrame-firstFrame;
			int framesRead;
 
			currentFrame=firstFrame;
			System.out.println("playing file, firstFrame="+firstFrame+" lastFrame="+lastFrame);
 
			try
			{
				line.start();
				if(firstFrame > 0)
				{
					long sa = firstFrame * frameSize;
					System.out.println("Skipping "+firstFrame+" frames="+sa+" bytes");
					while(sa > 0)sa -= stream.skip(sa);
				}
 
				while (running && framesRemaining > 0)
				{
					len = stream.read(buf,0,writeSize); // read our block
					if(len > 0)
					{
						framesRead = len/frameSize;
						framesRemaining -= framesRead;
						currentTime = currentFrame/frameRate;
						if(currentTime < 0)throw(new Exception("time too low"));
						System.out.println("time="+currentTime+" writing "+len+" bytes");
						line.write(buf,0,len);
						currentFrame+=framesRead;
					}
					else framesRemaining = 0;
				}
 
				if(running)
				{
					line.drain(); 				// let it play out
					while(line.isActive() && running)
					{
						System.out.println("line active");
						Thread.sleep(100);
					}
					shutdown = true;
				}
				running = false;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				shutdown = true;
				running = false;
			}
 
			if(shutdown)
			{
				close();
			}
		}
	}
 
 
 
 
	// return current time relative to start of sound
	public float getTime()
	{
		return ((float)line.getMicrosecondPosition())/1000000;
	}
 
	// return total time
	public float getLength()
	{
		return (float)frames / frameRate;
	}
 
	// stop the sound playback, return time in seconds
	public float  pause()
	{
		running = false;
		line.stop();
		return getTime();
	}
 
	public void update(LineEvent le)
	{
		System.out.println("Line event"+le.toString());
	}
 
 
	// play a short simple PCM encoded clip
	public static void playShortClip(String fnm)
	{
		java.applet.AudioClip clip=null;
		try
		{
			java.io.File file = new java.io.File(fnm);	// get a file for the name provided
			if(file.exists())				// only try to use a real file
			{
				clip = java.applet.Applet.newAudioClip(file.toURL()); // get the audio clip
			}
			else
				System.out.println("file="+fnm+" not found");
		}
		catch(Exception e)
		{
			System.out.println("Error building audio clip from file="+fnm);
			e.printStackTrace();
		}
 
		if(clip != null)	// we may not actually have a clip
		{
			final java.applet.AudioClip rclip = clip;
			new Thread
			(new Runnable()
				{
					public void run(){rclip.play();}
				}
			).start();
		}
	}
 
	public boolean isOpened(){return opened;}
	public boolean isRunning(){return running;}
}