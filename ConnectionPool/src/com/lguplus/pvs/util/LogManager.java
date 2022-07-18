package com.lguplus.pvs.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class LogManager {
	public enum LEVEL {
		TRACE(0, "TRACE"){
			void apply(Logger logger) {
				logger.setLevel(Level.FINE);
			}
		},
		DEBUG(1, "DEBUG"){
			void apply(Logger logger) {
				logger.setLevel(Level.FINE);
			}
		},
		INFO(2, "INFO"){
			void apply(Logger logger) {
				logger.setLevel(Level.INFO);
			}
		},
		WARN(3, "WARN"){
			void apply(Logger logger) {
				logger.setLevel(Level.WARNING);
			}
		},
		ERROR(4, "ERROR"){
			void apply(Logger logger) {
				logger.setLevel(Level.SEVERE);
			}
		};
		
		public final int intLevel;
		public final String strLevel;

		private LEVEL(int iLevel, String sLevel) {
			this.intLevel = iLevel;
			this.strLevel = sLevel;
		}
		
		abstract void apply(Logger logger);
		
		public static LEVEL getLevel(String sLevel) {
			String upperLevel = sLevel.toUpperCase();
			for(LEVEL level : LEVEL.values()) {
				if(level.strLevel.equals(upperLevel)) {
					return level;
				}
			}
			return INFO;
		}
		
		public static LEVEL getLevel(int intLevel) {
			for(LEVEL level : LEVEL.values()) {
				if(level.intLevel == intLevel) {
					return level;
				}
			}
			return INFO;
		}
		
	}

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
	        SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
	        Date resultdate = new Date(millisecs);
	        return date_format.format(resultdate);
	    }
	}

	private final static Logger mLogger = Logger.getGlobal();
	private static Handler OutputHandler = new ConsoleHandler();
	private LEVEL mLevel = LEVEL.INFO;

	private static LogManager instance = new LogManager();
	public static LogManager getInstance() { 
		return instance; 
	}

	public LogManager( ) {
		mLevel = LEVEL.INFO;
		mLevel.apply(mLogger);
        OutputHandler.setFormatter(new CustomLogFormatter());
		mLogger.addHandler(OutputHandler);
	}
	
	public LEVEL getLevel() {
		return mLevel;
	}

	public void setLevel(String level) {
		if(level == null) return;
		mLevel = LEVEL.getLevel(level);
		mLevel.apply(mLogger);
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
