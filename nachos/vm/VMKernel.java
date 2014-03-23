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
		 swapFile = fileSystem.open("1_0", true);
		 /*add it to swap table, this file will be overwritten on invocation of process 1*/
		 Hashtable<Integer, Boolean> vpn = new Hashtable<Integer, Boolean>();
		 vpn.put(1, false);
		 swapTable.put(1,vpn);
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
		swapFile.write(page, 0, page.length);
		
		return true;

	}
	
	public static byte[] readFromSwap(int PID, int VPN){
		byte[] page = null;
		
		swapFile.close();
		swapFile = fileSystem.open(Integer.toString(PID) + "_" + Integer.toString(VPN), false);
		
		/* Assert that page file should be present for it to be read from swap*/
		//Lib.assertTrue(!(swapFile == null), "Page not found in swap");
		
		if(swapFile != null)
		{
			swapFile.read(page, 0, pageSize);
			swapFile.close();
		}
		
		return page;
	}
	
	public static boolean isSwapPageReadOnly(int PID, int VPN)
	{
		return swapTable.get(PID).get(VPN);
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
		/* This is to delete the swap file that was created for process 1
		 * if present
		 */
		clearAllSwapFiles(1);
		super.terminate();
	}
	
	private static void clearAllSwapFiles(int PID)
	{
		if(swapTable.contains(PID))
		{
			Set<Integer> VPNs = swapTable.get(PID).keySet();
			
			for(int VPN : VPNs)
			{
				boolean status = fileSystem.remove(Integer.toString(PID) + "_" + Integer.toString(VPN));
				
				Lib.assertTrue(status, "Swap file not removed");
			}
			
			swapTable.remove(PID);
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
