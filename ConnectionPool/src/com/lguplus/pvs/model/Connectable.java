package com.lguplus.pvs.model;

public interface Connectable {
    public boolean Open(String server, int port, int timeout) throws Exception;
    public void Close() throws Exception;
    public int Read(byte[] receiveBuffer, int offset, int maxLength, int timeout) throws Exception;
    public int ReadN(byte[] receiveBuffer, int offset, int fixLength, int timeout) throws Exception;
    public int Write(byte[] writeBuffer, int offset, int size, int timeout) throws Exception;
	public boolean equals(Object o);
	public int hashCode();
	public String toString();
	public abstract String getMode();
}
