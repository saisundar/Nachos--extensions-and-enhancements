package nachos.vm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;

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
		
		freephysicalpages = new LinkedList<Integer>();

		//i = 0 is reserved for load operation
        for(int i = 1; i < Machine.processor().getNumPhysPages(); i++)
        {
        	freephysicalpages.add(i);
        }
        
		super.initialize(args);
	}

	public static boolean writeToSwap(int PID, int VPN, byte[] page){

		
		Lib.assertTrue(page.length == pageSize, "Incorrect Page size");
		
		OpenFile swapFile = fileSystem.open(Integer.toString(PID) + "_" + Integer.toString(VPN), true);
		
		/* If problem with IO then terminate process*/
		if(swapFile == null)
		{
			return false;
		}
		
		HashSet<Integer> VPNs = null;
		
		/* if no swap space for process allocated previously*/
		if(!swapTable.containsKey(PID))
		{
			VPNs = new HashSet<Integer>();
			
			VPNs.add(VPN);
			
			swapTable.put(PID, VPNs);
		}
		else
		{
			VPNs = swapTable.get(PID);
			
			VPNs.add(VPN);
		}
		
		/* write to swap file*/
		swapFile.write(page, 0, page.length);
		
		return true;

	}
	
	public static byte[] readFromSwap(int PID, int VPN){
		byte[] page = null;
		
		OpenFile swapFile = fileSystem.open(Integer.toString(PID) + "_" + Integer.toString(VPN), false);
		
		/* Assert that page file should be present for it to be read from swap*/
		//Lib.assertTrue(!(swapFile == null), "Page not found in swap");
		
		if(swapFile != null)
		{
			swapFile.read(page, 0, pageSize);
			swapFile.close();
		}
		
		return page;
	}
	
	public static void exitProcess(int PID){
		
		/* clear all swap files*/
		clearAllSwapFiles(PID);
		
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
	
	private static void clearAllSwapFiles(int PID)
	{
		HashSet<Integer> VPNs = swapTable.get(PID);
		
		for(int VPN : VPNs)
		{
			boolean status = fileSystem.remove(Integer.toString(PID) + "_" + Integer.toString(VPN));
			
			Lib.assertTrue(status, "Swap file not removed");
		}
		
		swapTable.remove(PID);
	}

	public static int getfreepage()
	{
		if (freephysicalpages.isEmpty())
			return -1;
		
		return freephysicalpages.removeFirst();
	}
	
	public static void setfreepage(int ppn)
	{   
		freephysicalpages.add(ppn);
	}

	public static int getNumOfFreePages(){
		return freephysicalpages.size();
	}
	
	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';
	
	//key is PPN, value<0, 1> 0:pid 1:VPN
	private static Hashtable<Integer, ArrayList<Integer>> invPageTable = null;
	//key is PID, value is List of VPN of process in swap
	private static Hashtable<Integer, HashSet<Integer>> swapTable = null;
	/*Page size*/
	private static final int pageSize = Processor.pageSize;
	
	private static LinkedList<Integer> freephysicalpages;
}
