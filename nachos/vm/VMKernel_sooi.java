package nachos.vm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.HashMap;
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
  
    public class pageTableEntry {
		
		TranslationEntry tE;
		public int pid;
		
//		public TranslationEntry(int vpn, int ppn, boolean valid, boolean readOnly,
//				boolean used, boolean dirty) {
		
		pageTableEntry(int PID,int VPN, int PPN, boolean val , boolean RO, boolean use, boolean dirty)
		{
			pid=PID;
			tE=new TranslationEntry(VPN, PPN ,val , RO , use, dirty);							
		}
		
	}
       
    public class virtualNumKey{
    	
    	public int vpn;	
		
		public int pid; 
		
		public virtualNumKey(int PID, int VPN)
		{
			vpn=VPN;
			pid=PID;
			
		}
		public boolean equals(Object obj) {
			if(obj==null)return false;
			virtualNumKey temp = (virtualNumKey)obj;
			return((this.vpn==temp.vpn) && (this.pid==temp.pid));
		}
		
		 public int hashCode(){
			 final int prime = 97;
			 return((prime*pid)+vpn);
		 }
    }
    
    
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
		LRUList = new LinkedList<pageTableEntry>();
		LRUmap = new HashMap<virtualNumKey,pageTableEntry>();


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
	
	
	//needs to be invoked from handlExit with the PId and the num of virtual pages the process can legally access
	public static void clearPagesOfProcess(int PID,int numPages){
		
		virtualNumKey temp = new virtualNumKey(PID,0);
		pageTableEntry entry ;
		for(temp.vpn=0;temp.vpn<numPages;temp.vpn++)
		{
			if(LRUmap.containsKey(temp)){
				entry = LRUmap.get(temp);
				if(entry!=null)
				{
					LRUList.remove(entry);
					freephysicalpages.add(entry.tE.ppn);
				}
				LRUmap.remove(temp);
			}
		}
	}
	
	
	// requires routine to see if given vpn is readonly or not... has to be added here.....
	public static int readSwapIntoPhys(int PPN, int PID, int VPN){
		
		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (VPN < 0 )
			return 0;

		byte[] swap = readFromSwap(PID,VPN);
		
		if(swap!=null)
		{
			System.arraycopy(swap, 0, memory, PPN*pageSize, swap.length);
			Lib.assertTrue(swap.length==pageSize, " why the hell is swap sie not equal to a page");	
			
			///////////// ROUTINE to be addded here if given page is readonly or not.........
			boolean isRO = true;// invoke routine to deterimine readonly or not of the virtualpage.
					
			if(isRO)
				return 1;
			return 0;
		}
		return -1;
	}
	
	public static int writePhysIntoSwap(int PPN, int PID, int VPN)
	{
		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (VPN < 0 )
			return 0;
		byte[] tempPhysPage = new byte[pageSize];
		System.arraycopy(memory, PPN*pageSize, tempPhysPage, 0, pageSize);
		if(!writeToSwap(PID,VPN,tempPhysPage))
			return -1;
		
		
		return 0;
	}
	
	public static String printLRUsnapShot()
	{
		Lib.debug(dbgProcess, "<LRU snapshot===============from MRU to LRU==========================>");	
		Lib.debug(dbgProcess, "number of free physicalpages =" + freephysicalpages.size());	
		Lib.debug(dbgProcess, "number of physicalpages occupied =" + LRUList.size());
		
		System.out.format("%10%10s%10s%10s%10s%10s%10s","PID", "PPN", "VPN", "Valid ", "ReadOnly","Used","Dirty");
		
		for(pageTableEntry p : LRUList)
		{
			System.out.format("%10d%1ds%10d%10d%10d%10d%10d",p.pid, p.tE.ppn, p.tE.ppn, p.tE.valid, p.tE.readOnly,p.tE.used,p.tE.dirty);
		}
		
		Lib.debug(dbgProcess, "</LRU snapshot=========================================================>");
		return null;
	}

	//1)assuming all alegality of address checking is done before.
	//2) assuming that when the address comes here, it has already verified checks for valid range.
	//3)
	//4) if translation entry is null the only reason for that would be that some error occurred while swapping. so kill process.
	public static TranslationEntry getPPN(int PID, int VPN,boolean calledfromTLBMiss){
		
		virtualNumKey temp = new virtualNumKey(PID,VPN);
		Lib.debug(dbgProcess, "translating VPN to PPN trnalsation entry...");
		Lib.debug(dbgProcess, "requesting for PID="+PID);
		Lib.debug(dbgProcess, "requesting for VPN="+VPN);
		mutex.acquire();
		if(LRUmap.containsKey(temp)){
     			// entry is there in the page table. so return the physical address.
			  pageTableEntry entry = LRUmap.get(temp);
			  Lib.debug(dbgProcess, "1.1 entry already in LRU invrted page table.. so moving to front while retrieving..");
			  LRUList.remove(entry);
			  LRUList.addFirst(entry);
			  if(calledfromTLBMiss)
				{
				  addTLBentry(entry.tE);
				}
				else
				{
				// no udation of TLB required here, assuming localities skewed due to OS-processor domain shift.	
				}
			
			  Lib.debug(dbgProcess, printLRUsnapShot());
			  mutex.release();
	          return entry.tE;
		}
		else
		{
			//entry is not there in the page table. so you need to consider two options.
//			1) there is enough physical memory available to accomodate the new page-- no replacement
//			2) there is not enough memory there to accomodate the page. hence, need to replace the least recently used one.
			
			if(LRUList.size()<Machine.processor().getNumPhysPages()-1)
			{
				int phys = getfreepage();
				Lib.assertTrue(phys!=-1, " oops net free physcial page returned as -1 even thoguh free space is avaliable");
				 Lib.debug(dbgProcess, "2.1 Entry not in pagetable but free space availalbe so moving in from swap to free page");
				int type = readSwapIntoPhys(phys,PID,VPN);
				
				if(type!=-1)
					//int PID,int VPN, int PPN, boolean val , boolean RO, boolean use, boolean dirty
				{
					if(calledfromTLBMiss)
					{
						addTLBentry(entry.tE);
					}
					else
					{
						// no udation of TLB required here, assuming localities skewed due to OS-processor domain shift.
					}
					pageTableEntry entry = new pageTableEntry(PID,VPN,phys,true,type==1?true:false,true,false);
					LRUList.addFirst(entry);
					LRUmap.put(temp,entry);
					Lib.debug(dbgProcess, printLRUsnapShot());
					mutex.release();

					return entry.tE;
				}
				
				return null;
			}
			if(LRUList.size()==Machine.processor().getNumPhysPages()-1)
			{
				Lib.debug(dbgProcess, "2.2 Entry not in pagetable and no free space availalbe so replacing LRU");
				pageTableEntry entry= LRUList.removeLast();
				virtualNumKey temp1 = new virtualNumKey(entry.pid,entry.tE.vpn);
				LRUmap.remove(temp1);
				//remove tlb entry if required
				int phys= entry.tE.ppn;
				
				if(entry.tE.dirty)	{
					Lib.debug(dbgProcess, "		2.2.1 LRU entry is dirty so writing back to swap space");
					int error=writePhysIntoSwap(entry.tE.ppn,entry.pid, entry.tE.vpn);
					if (error==-1)
					{
						Lib.debug(dbgProcess, printLRUsnapShot());
						mutex.release();
						return null;		
					}
				}
				int type = readSwapIntoPhys(phys,PID,VPN);
				if(type!=-1)
					//int PID,int VPN, int PPN, boolean val , boolean RO, boolean use, boolean dirty
				{
					pageTableEntry newEntry = new pageTableEntry(PID,VPN,phys,true,type==1?true:false,true,false);
					LRUList.addFirst(newEntry);
					LRUmap.put(temp,newEntry);
					
					invalidateTLB(entry.tE);
					if(calledfromTLBMiss)
					{
						addTLBentry(newEntry.tE);
					}
					else
					{
						// no udation of TLB required here, assuming localities skewed due to OS-processor domain shift.
					}
					
					mutex.release();
					return newEntry.tE;
				}
				Lib.debug(dbgProcess, printLRUsnapShot());
				return null;
			}
			mutex.release();
		}
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
	
//	//key is PPN, value<0, 1> 0:pid 1:VPN
//	private static Hashtable<Integer, ArrayList<Integer>> invPageTable = null;
//	//key is PID, value is List of VPN of process in swap
//	private static Hashtable<Integer, HashSet<Integer>> swapTable = null;
	/*Page size*/
	public static LinkedList<pageTableEntry> LRUList;
    public static HashMap<virtualNumKey,pageTableEntry> LRUmap;
    
	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static LinkedList<Integer> freephysicalpages;
	private static Lock mutex = new Lock();
}
