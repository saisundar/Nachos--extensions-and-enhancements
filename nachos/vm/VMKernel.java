package nachos.vm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	private static int count;
    private static ArrayList<Integer> tlbmap= new ArrayList<Integer>();
	public VMKernel()
	{
	   	 super();
	   	 count=0;
		 int tlbsize= Machine.processor().getTLBSize();
		 for(int i=0;i<tlbsize;i++)
		 {
			 tlbmap.add(0);
		 }
			
	}
	public static int getTLBReplacePosition()
	{   
		int tlbsize= Machine.processor().getTLBSize();
		int min=tlbmap.get(0);
		for(int i=1;i<tlbsize;i++)
		 {
			 if(tlbmap.get(i)<min)
				 min=tlbmap.get(i);
		 }
		return min;
	}
    public static void setNewTLBEntry(int index)
    {
    	count++;
    	tlbmap.set(index, count);
    	
    }
	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		invPageTable = new Hashtable<Integer, ArrayList<Integer>>();
		swapTable = new Hashtable<Integer, HashSet<Integer>>();
		
		super.initialize(args);
	}


	public static void writeToSwap(int PID, int VPN, byte[] page){
		
	}
	
	public static byte[] readFromSwap(int PID, int VPN){
		byte[] page = null;
		return page;
	}
	
	public static void exitProcess(int PID){
		
	}
	
	public static void addToPageTable(int PPN, int PID, int VPN){
		
	}
	
	public static int getPPN(int PID, int VPN){
		
		return -1; //negative to indicate the page is not available
	}
	
	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';
	
	//key is PPN, value<0, 1> 0:pid 1:VPN
	private static Hashtable<Integer, ArrayList<Integer>> invPageTable = null;
	//key is PID, value is List of VPN of process in swap
	private static Hashtable<Integer, HashSet<Integer>> swapTable = null;
}
