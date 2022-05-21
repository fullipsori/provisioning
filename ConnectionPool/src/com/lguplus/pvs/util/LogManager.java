package com.lguplus.pvs.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class LogManager {

	private class CustomLogFormatter extends Formatter {
	    
	    public String format(LogRecord rec) {
	        StringBuffer buf = new StringBuffer(1000);
	        buf.append(calcDate(rec.getMillis()));
	        
	        buf.append(" [");
	        buf.append(rec.getLevel());
	        buf.append("] ");
	        
	        /**
	        buf.append("[");
	        buf.append(rec.getSourceMethodName());
	        buf.append("] ");
	        **/
	        
	        buf.append(rec.getMessage().trim());
	        buf.append("\n");
	        
	        return buf.toString();
	    }
	    
	    private String calcDate(long millisecs) {
	        SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	        Date resultdate = new Date(millisecs);
	        return date_format.format(resultdate);
	    }
	}

	private final static Logger mLogger = Logger.getGlobal();
	private static Handler OutputHandler = new ConsoleHandler();

	private static LogManager instance = new LogManager();
	public static LogManager getInstance() { 
		return instance; 
	}

	public LogManager( ) {
		mLogger.setLevel(Level.INFO);
        OutputHandler.setFormatter(new CustomLogFormatter());
		mLogger.addHandler(OutputHandler);
	}
	
	public void setLevel(String level) {
		if(level == null) return;
		if(level.equalsIgnoreCase("error")) mLogger.setLevel(Level.SEVERE);
		else if(level.equalsIgnoreCase("warn")) mLogger.setLevel(Level.WARNING);
		else if(level.equalsIgnoreCase("debug")) mLogger.setLevel(Level.FINE);
		else mLogger.setLevel(Level.INFO);
	}
	
	public void setOutputMode(boolean fileMode, String filename) {
		try{
			if(fileMode) {
				Handler outHandler = new FileHandler(filename, true);
				mLogger.removeHandler(OutputHandler);
				OutputHandler = outHandler;
				OutputHandler.setFormatter(new CustomLogFormatter());
				mLogger.addHandler(OutputHandler);
			}else {
				if(!(OutputHandler instanceof ConsoleHandler)) {
					mLogger.removeHandler(OutputHandler);
					OutputHandler = new ConsoleHandler();
					OutputHandler.setFormatter(new CustomLogFormatter());
					mLogger.addHandler(OutputHandler);
				}
			}
		}catch(Exception e) {
			System.out.println("Exception:" + e.getMessage());
		}
	}

	public void error(String msg) {
		mLogger.log(Level.SEVERE, msg);
	}
	
	public void warn(String msg) {
		mLogger.log(Level.WARNING, msg);
	}
	
	public void info(String msg) {
		mLogger.log(Level.INFO, msg);
	}
	
	public void debug(String msg) {
		mLogger.log(Level.FINE, msg);
	}
}
