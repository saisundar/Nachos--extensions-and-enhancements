package nachos.vm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Set;

import nachos.machine.*;
import nachos.userprog.*;

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
		 
		 /* Open swap file for first process 0th page. This is to reserve a file for the swap file as
		  * the file system allows only 16 files to be open at a time
		  */
		 swapFile = fileSystem.open("dummy", true);
	}
	
	private int getTLBReplacePosition()
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
	
    private void setNewTLBEntry(int index)
    {
    	count++;
    	tlbmap.set(index, count);
    	
    }
    
    private static void resetTLBEntry(int index){
    	tlbmap.set(index, 0);
    }
    
	public static void clearTLBEntries(){
		int tlbsize= Machine.processor().getTLBSize();
     	
		for(int i=0;i<tlbsize;i++)
     	{
     		TranslationEntry tlbentry=Machine.processor().readTLBEntry(i);
     		if(tlbentry.valid)
     		{
     			tlbentry.valid=false;
     			Machine.processor().writeTLBEntry(i, tlbentry);
     			resetTLBEntry(i);
     		}
   	  	}
		count = 0;
	}
	
	private boolean invalidateTLB(TranslationEntry tle)
	{ 
		boolean updated=false;
	    int tlbsize= Machine.processor().getTLBSize();
		for(int i=0;i<tlbsize;i++)
     	{
     		TranslationEntry tlbentry=Machine.processor().readTLBEntry(i);
     		if(tlbentry.valid && (tle.ppn==tlbentry.ppn))
     		{
     			updated=true;
     			tlbentry.valid=false;
     			Machine.processor().writeTLBEntry(i, tlbentry);
     			resetTLBEntry(i);
     			break;
     		}
   	  	}

		return updated;
	}
	
	private boolean addTLBentry(TranslationEntry tle)
	{
		    boolean updated=false;
	        int tlbsize= Machine.processor().getTLBSize();
	     	
	     	//check if tlb is full or not
	     	for(int j=0;j<tlbsize;j++)
	     	{
	     		TranslationEntry tlbentry=Machine.processor().readTLBEntry(j);
	     		if(tlbentry.valid == false)
	     		{
	     			updated=true;
	     			tle.valid=true;
	     			Machine.processor().writeTLBEntry(j, tle);
	     			setNewTLBEntry(j);
	     			break;
	     		}
	     	}
	     	
	     	if(updated)
	     	  return updated;
	     	else
	     	{
	     		int pos=getTLBReplacePosition();
	     		tle.valid=true;
	     		updated=true;
	     		Machine.processor().writeTLBEntry(pos, tle);
	     		setNewTLBEntry(pos);
	     	}
	     	
	     	return updated;
	}
	
	
	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		invPageTable = new Hashtable<Integer, ArrayList<Integer>>();
		swapTable = new Hashtable<Integer, Hashtable<Integer, Boolean>>();
		
		freephysicalpages = new LinkedList<Integer>();

		//i = 0 is reserved for load operation
        for(int i = 1; i < Machine.processor().getNumPhysPages(); i++)
        {
        	freephysicalpages.add(i);
        }
        
		super.initialize(args);
	}

	public static boolean writeToSwap(int PID, int VPN, byte[] page, boolean readOnlyFlag){

		
		Lib.assertTrue(page.length == pageSize, "Incorrect Page size");
		
		swapFile.close();
		swapFile = fileSystem.open(Integer.toString(PID) + "_" + Integer.toString(VPN), true);
		
		/* If problem with IO then terminate process*/
		if(swapFile == null)
		{
			/* Open dummy swapFile*/
			swapFile = fileSystem.open("dummy", false);
			return false;
		}
		
		Hashtable<Integer, Boolean> VPNs = null;
		
		/* if no swap space for process allocated previously*/
		if(!swapTable.containsKey(PID))
		{
			VPNs = new Hashtable<Integer, Boolean>();
			
			VPNs.put(VPN, readOnlyFlag);
			
			swapTable.put(PID, VPNs);
		}
		else
		{
			VPNs = swapTable.get(PID);
			
			VPNs.put(VPN, readOnlyFlag);
		}
		
		/* write to swap file*/
		int writeLength = swapFile.write(page, 0, page.length);
		
		swapFile.close();
		swapFile = fileSystem.open("dummy", false);
		
		/* write to swapFile unsuccessful return false*/
		if(writeLength != page.length)
			return false;
		
		return true;

	}
	
	public static byte[] readFromSwap(int PID, int VPN){
		byte[] page = null;
		
		swapFile.close();
		swapFile = fileSystem.open(Integer.toString(PID) + "_" + Integer.toString(VPN), false);
		
		/* Assert that page file should be present for it to be read from swap*/
		//Lib.assertTrue(!(swapFile == null), "Page not found in swap");
		
		int readLength = 0;
		
		if(swapFile != null)
		{
			readLength = swapFile.read(page, 0, pageSize);
			swapFile.close();
		}
		
		/*Open dummy*/
		swapFile = fileSystem.open("dummy", false);
		
		/*Read failed return null*/
		if(readLength != pageSize)
			page = null;
		
		return page;
	}
	
	public static boolean isSwapPageReadOnly(int PID, int VPN)
	{
		return swapTable.get(PID).get(VPN);
	}
	
	public static void exitProcess(int PID){
		
		/* clear all swap files of process*/
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
		/* clear all swap files if present*/
		if(swapFile != null)
		{
			swapFile.close();
		}
		
		fileSystem.remove("dummy");
		
		super.terminate();
	}
	
	private static void clearAllSwapFiles(int PID)
	{
		if(swapTable.containsKey(PID))
		{
			Set<Integer> VPNs = swapTable.get(PID).keySet();
			
			for(int VPN : VPNs)
			{
				boolean status = fileSystem.remove(Integer.toString(PID) + "_" + Integer.toString(VPN));
				
				Lib.assertTrue(status, "Swap file not removed");
			}
		}
	}

	public static int getReservedPhyPage(){
		return 0;
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
	//key is PID, value is List of <VPN, readOnlyFlag> of process in swap
	private static Hashtable<Integer, Hashtable<Integer, Boolean>> swapTable = null;
	/*Page size*/
	private static final int pageSize = Processor.pageSize;
	
	private static LinkedList<Integer> freephysicalpages;
	
	private static OpenFile swapFile;
}
