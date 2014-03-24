package nachos.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Set;

import nachos.machine.*;
import nachos.threads.Lock;
import nachos.userprog.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */   
	public VMKernel()
	{
	   	 super();
	}
    
    public class pageTableEntry {
		
		TranslationEntry tE;
		public int pid;
		
//		public TranslationEntry(int vpn, int ppn, boolean valid, boolean readOnly,
//				boolean used, boolean dirty) {
		
		public pageTableEntry(int PID,int VPN, int PPN, boolean val , boolean RO, boolean use, boolean dirty)
		{
			pid=PID;
			tE=new TranslationEntry(VPN, PPN ,val , RO , use, dirty);							
		}
	}
       
    public class virtualNumKey{
    	
    	private int vpn;	
		
		private int pid; 
		
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
	
	private static int getTLBReplacePosition()
	{   
		int tlbsize= Machine.processor().getTLBSize();
		int minVal=tlbmap.get(0);
		int min = 0;
		for(int i=1;i<tlbsize;i++)
		 {
			 if(tlbmap.get(i)<minVal){
				 min=i;
				 minVal = tlbmap.get(i);
			 }
		 }
		return min;
	}
	
    private static void setNewTLBEntry(int index)
    {
    	count++;
    	tlbmap.set(index, count);
    	
    }
    
    private static void resetTLBEntry(int index){
    	tlbmap.set(index, 0);
    }
    
	public static String printTLBSnapShot()
	{
		/*
		Lib.debug(dbgProcess, "<TLB snapshot=========================================>");	
		Lib.debug(dbgProcess, "number of Entries  =" + Machine.processor().getTLBSize());	
		
		System.out.format("%10s%10s%10s%10s%10s%10s%10s%10s\n","Index", "Count", "PPN", "VPN", "Valid ", "ReadOnly","Used","Dirty");
		int tlbsize= Machine.processor().getTLBSize();
		TranslationEntry tE = null;
		for(int i=0;i<tlbsize;i++)
		{
			tE = Machine.processor().readTLBEntry(i);
			if (tE.valid)
				System.out.format("%10d%10d%10d%10d%10d%10d%10d%10d\n",i, tlbmap.get(i),tE.ppn, tE.vpn, tE.valid?1:0, tE.readOnly?1:0, tE.used?1:0, tE.dirty?1:0);
		}
		
		Lib.debug(dbgProcess, "</TLB snapshot========================================>");
		*/
		return null;
	}
	
	public static void updateIfPresentTLB(TranslationEntry e)
	{
		Lib.debug(dbgProcess, "updateIfPresentTLB(TranslationEntry e) " +  e.ppn);
		int tlbsize= Machine.processor().getTLBSize();
		TranslationEntry TLBEntry = null;
		for(int i=0;i<tlbsize;i++)
     	{
     		TLBEntry = Machine.processor().readTLBEntry(i);
     		if(TLBEntry.ppn==e.ppn && TLBEntry.vpn==e.vpn && TLBEntry.valid)
     		{
     			e.dirty=TLBEntry.dirty;
     			e.used=TLBEntry.used;
     			//set the dirty and used flags of tlb entry to invpage table to keep in sync
     		}
   	  	}
		
	}
	public static void clearTLBEntries(int pid){
		Lib.debug(dbgProcess, "clearTLBEntries(int pid) = " + pid);
		int tlbsize= Machine.processor().getTLBSize();
		TranslationEntry TLBEntry = null;
		TranslationEntry PTEntry = null;
		for(int i=0;i<tlbsize;i++)
     	{
     		TLBEntry = Machine.processor().readTLBEntry(i);
     		if(TLBEntry.valid)
     		{
     			TLBEntry.valid=false;
     			//set the dirty and used flags of tlb entry to invpage table to keep in sync
     			PTEntry = getPTEntry(pid, TLBEntry.vpn);
     			if (null != PTEntry){
         			PTEntry.dirty = TLBEntry.dirty;
         			PTEntry.used = TLBEntry.used;    				
     			}
     			
     			Machine.processor().writeTLBEntry(i, TLBEntry);
     			resetTLBEntry(i);
     		}
   	  	}
		count = 0;
		Lib.debug(dbgProcess, printTLBSnapShot());
	}
	
	private static boolean invalidateTLB(TranslationEntry tle)
	{ 
		Lib.debug(dbgProcess, "invalidateTLB(TranslationEntry tle) = " + tle.ppn);
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


		Lib.debug(dbgProcess, printTLBSnapShot());
		return updated;
	}
	
	private static boolean addTLBentry(TranslationEntry tle,int PID)
	{
		Lib.debug(dbgProcess, "addTLBentry(TranslationEntry tle,int PID) = " + tle.ppn + " " + PID);
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
	     	
	     	if (!updated)
	     	{
	     		int pos=getTLBReplacePosition();
	     		
	     		TranslationEntry tlbentry=Machine.processor().readTLBEntry(pos);
	     		TranslationEntry temp;
	     		temp=getPTEntry(PID, tlbentry.vpn);
	     		if (null != temp){
		     		temp.used=tlbentry.used;
		     		temp.dirty=tlbentry.dirty;	     			
	     		}
	     		
	     		tle.valid=true;
	     		updated=true;
	     		Machine.processor().writeTLBEntry(pos, tle);
	     		setNewTLBEntry(pos);
	     	}
	     	

			Lib.debug(dbgProcess, printTLBSnapShot());
	     	return updated;
	}
	
	
	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {	
		super.initialize(args);
		
		swapTable = new Hashtable<Integer, Hashtable<Integer, Boolean>>();
		
		freephysicalpages = new LinkedList<Integer>();

		//i = 0 is reserved for load operation
        for(int i = 1; i < Machine.processor().getNumPhysPages(); i++)
        {
        	freephysicalpages.add(i);
        }
        
        Lib.debug(dbgProcess, "Free size : " + freephysicalpages.size());
        
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
		
		mutex = new Lock();

	    LRUList = new LinkedList<pageTableEntry>();
	    LRUmap = new HashMap<virtualNumKey,pageTableEntry>();
	}

	public static boolean writeToSwap(int PID, int VPN, byte[] page, boolean readOnlyFlag){

		Lib.debug(dbgProcess, "START writeToSwap(int PID, int VPN) " +  PID + " " + VPN);
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
		
		Lib.debug(dbgProcess, "END: writeToSwap(int PID, int VPN) " +  PID + " " + VPN);
		/* write to swapFile unsuccessful return false*/
		if(writeLength != page.length)
			return false;
		
		return true;

	}
	
	public static byte[] readFromSwap(int PID, int VPN){
		byte[] page = new byte[pageSize];
		
		Lib.debug(dbgProcess, "START : readFromSwap(int PID, int VPN) " +  PID + " " + VPN);
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
		
		Lib.debug(dbgProcess, "END : readFromSwap(int PID, int VPN) " +  PID + " " + VPN);
		return page;
	}
	
	public static boolean isSwapPageReadOnly(int PID, int VPN)
	{
		return swapTable.get(PID).get(VPN);
	}
	
	
	//needs to be invoked from handlExit with the PId and the num of virtual pages the process can legally access
	public static void clearPagesOfProcess(int PID,int numPages){
		Lib.debug(dbgProcess, "clearPagesOfProcess(int PID, int numPages) " +  PID + " " + numPages);
		int vpn=0;
		VMKernel obj=((VMKernel)Kernel.kernel);
		virtualNumKey temp = obj.new virtualNumKey(PID,vpn);
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
		
		clearAllSwapFiles(PID);
		
	}
	
	
	public static int readSwapIntoPhys(int PPN, int PID, int VPN){
		
		byte[] memory = Machine.processor().getMemory();

		Lib.debug(dbgProcess, "readSwapIntoPhys(int PPN, int PID, int VPN) " +  PPN + " " + PID + " " + VPN);
		// for now, just assume that virtual addresses equal physical addresses
		if (VPN < 0 )
			return 0;

		byte[] swap = readFromSwap(PID,VPN);
		
		if(swap!=null)
		{
			System.arraycopy(swap, 0, memory, PPN*pageSize, swap.length);
			Lib.assertTrue(swap.length==pageSize, " why the hell is swap sie not equal to a page");	
			
			boolean isRO = isSwapPageReadOnly(PID, VPN);
			
			if(isRO)
				return 1;
			return 0;
		}
		return -1;
	}
	
	public static int writePhysIntoSwap(int PPN, int PID, int VPN)
	{
		Lib.debug(dbgProcess, "writePhysIntoSwap(int PPN, int PID, int VPN) " +  PPN + " " + PID + " " + VPN);
		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (VPN < 0 )
			return 0;
		byte[] tempPhysPage = new byte[pageSize];
		System.arraycopy(memory, PPN*pageSize, tempPhysPage, 0, pageSize);
		if(!writeToSwap(PID,VPN,tempPhysPage,isSwapPageReadOnly(PID, VPN)))
			return -1;
		
		
		return 0;
	}
	
	public static String printLRUsnapShot()
	{
		/*
		Lib.debug(dbgProcess, "<LRU snapshot===============from MRU to LRU==========================>");	
		Lib.debug(dbgProcess, "number of free physicalpages =" + freephysicalpages.size());	
		Lib.debug(dbgProcess, "number of physicalpages occupied =" + LRUList.size());
		
		System.out.format("%10s%10s%10s%10s%10s%10s%10s\n","PID", "PPN", "VPN", "Valid ", "ReadOnly","Used","Dirty");
		
		for(pageTableEntry p : LRUList)
		{
			System.out.format("%10d%10d%10d%10d%10d%10d%10d\n",p.pid, p.tE.ppn, p.tE.vpn, p.tE.valid?1:0, p.tE.readOnly?1:0,p.tE.used?1:0,p.tE.dirty?1:0);
		}
		
		Lib.debug(dbgProcess, "</LRU snapshot=========================================================>");
		*/
		return null;
	}

	private static TranslationEntry getPTEntry(int PID, int VPN){
		VMKernel obj = ((VMKernel)Kernel.kernel);
		virtualNumKey temp = obj.new virtualNumKey(PID,VPN);
		TranslationEntry entry = null;
		
		if(LRUmap.containsKey(temp)){
			entry = LRUmap.get(temp).tE;
		}
		
		return entry;
	}
	
	//1)assuming all alegality of address checking is done before.
	//2) assuming that when the address comes here, it has already verified checks for valid range.
	//3)
	//4) if translation entry is null the only reason for that would be that some error occurred while swapping. so kill process.
	public static TranslationEntry getPPN(int PID, int VPN,boolean calledfromTLBMiss){
		VMKernel obj = ((VMKernel)Kernel.kernel);
		virtualNumKey temp = obj.new virtualNumKey(PID,VPN);
		Lib.debug(dbgProcess, "translating VPN to PPN trnalsation entry...");
		Lib.debug(dbgProcess, "requesting for PID="+PID);
		Lib.debug(dbgProcess, "requesting for VPN="+VPN);
		TranslationEntry ret = null;
		
		if (VPN == 651135){
			System.out.println();
			Thread.dumpStack();
		}
		mutex.acquire();
		
		if(LRUmap.containsKey(temp)){
     			// entry is there in the page table. so return the physical address.
			  pageTableEntry entry = LRUmap.get(temp);
			  Lib.debug(dbgProcess, "1.1 entry already in LRU invrted page table.. so moving to front while retrieving..");
			  LRUList.remove(entry);
			  LRUList.addFirst(entry);
			  if(calledfromTLBMiss)
				{
				  addTLBentry(entry.tE,PID);
				}
				else
				{
				// no udation of TLB required here, assuming localities skewed due to OS-processor domain shift.	
				}
			
			  Lib.debug(dbgProcess, printLRUsnapShot());
			  ret = entry.tE;
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
					pageTableEntry entry = obj.new pageTableEntry(PID,VPN,phys,true,type==1?true:false,true,false);
					LRUList.addFirst(entry);
					LRUmap.put(temp,entry);
					Lib.debug(dbgProcess, printLRUsnapShot());
					if(calledfromTLBMiss)
					{
						addTLBentry(entry.tE,PID);
					}
					else
					{
						// no udation of TLB required here, assuming localities skewed due to OS-processor domain shift.
					}
					
					ret = entry.tE;
				}
			}
			
			if ((ret != null) && (LRUList.size()==Machine.processor().getNumPhysPages()-1))
			{
				Lib.debug(dbgProcess, "2.2 Entry not in pagetable and no free space availalbe so replacing LRU");
				pageTableEntry entry= LRUList.removeLast();
				virtualNumKey temp1 = obj.new virtualNumKey(entry.pid,entry.tE.vpn);
				LRUmap.remove(temp1);
				//remove tlb entry if required
				int phys= entry.tE.ppn;
				updateIfPresentTLB(entry.tE);
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
					pageTableEntry newEntry = obj.new pageTableEntry(PID,VPN,phys,true,type==1?true:false,true,false);
					LRUList.addFirst(newEntry);
					LRUmap.put(temp,newEntry);
					
					invalidateTLB(entry.tE);
					if(calledfromTLBMiss)
					{
						addTLBentry(newEntry.tE,PID);
					}
					else
					{
						// no udation of TLB required here, assuming localities skewed due to OS-processor domain shift.
					}
					
					ret = newEntry.tE;
				}
				Lib.debug(dbgProcess, printLRUsnapShot());
			}
		}
		
		mutex.release();
		
		return ret;
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
	private static final char dbgProcess = 's';
	
	//key is PPN, value<0, 1> 0:pid 1:VPN
	private static Hashtable<Integer, Hashtable<Integer, Boolean>> swapTable = null;
	/*Page size*/
	private static final int pageSize = Processor.pageSize;
	
	private static LinkedList<Integer> freephysicalpages;
	
	private static OpenFile swapFile;

	private static int count;
    private static ArrayList<Integer> tlbmap= new ArrayList<Integer>();
 
    public static LinkedList<pageTableEntry> LRUList;
    public static HashMap<virtualNumKey,pageTableEntry> LRUmap;
    private static Lock mutex = null;

}
