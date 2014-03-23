package nachos.vm;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.Arrays;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		//invalidate tlb entries here
		//super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		return super.loadSections();
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Writes the COFF sections into SWAP. If this returns successfully, 
	 * 
	 * @return <tt>true</tt> if the sections were successfully written to swap.
	 */
	protected boolean writeCoffSections() {

		// write sections
		// the coff sections start from virtual page number 0
		int vpn = 0;
		byte[] memory = Machine.processor().getMemory();
		int paddr = VMKernel.getReservedPhyPage() * pageSize;
		
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				//int vpn = section.getFirstVPN() + i;
				byte[] page = new byte[pageSize];
				section.loadPage(i, VMKernel.getReservedPhyPage());
				System.arraycopy(memory, paddr, page, 0, pageSize);
				VMKernel.writeToSwap(pid, vpn, page);
				vpn++;

			}
		}

		Lib.assertTrue(vpn == numPages);
		
		return true;
	}
	
	private void writeStackSections(int stackSize){
	
		//write empty buffers to represent an empty stack
		int vpn = numPages;
		byte[] page = new byte[pageSize];
		
		for (int i = 0; i < stackSize; i++){
			VMKernel.writeToSwap(pid, vpn, page);
			vpn++;
		}
		
	}
	
	private boolean load(String name, String[] args) {
		//decide the number of pages required for this process
		//identify pagenums of stack and args
		//read the coff sections and write it to swap
		//create page for args and stack and write to swap
		
		Lib.debug(dbgProcess, "VMProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();
	
		if (!writeCoffSections())
			return false;

		// next comes the stack; stack pointer initially points to top of it
		writeStackSections(stackPages);
		numPages += stackPages;
		initialSP = numPages * pageSize;
		

		// and finally reserve 1 page for arguments
		numPages++;
		
		// store arguments in reserved physical page and write it to swap
		byte[] page = new byte[pageSize]; //is this initialized to 0s ?
		int entryOffset = 0;
		int stringOffset = entryOffset + args.length * 4;
		
		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			System.arraycopy(stringOffsetBytes, 0, page, entryOffset, 4);
			//Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			System.arraycopy(argv[i], 0, page, stringOffset, argv[i].length);
			//Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			System.arraycopy(new byte[] { 0 }, 0, page, stringOffset, 1);
			//Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}
		
		//write args page to swap
		VMKernel.writeToSwap(pid, numPages-1, page);
		
		return true;
	}
	
	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		new UThread(this).setName(name).fork();

		return true;
	}
	
	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}
	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss:
			handleTLBMiss(Machine.processor().readRegister(Processor.regBadVAddr));
			break;
		default:
			super.handleException(cause);
			break;
		}
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
     			break;
     		}
   	  	}
		return updated;
	}
	
	private boolean addTLBentry(TranslationEntry tle)
	{
		    boolean updated=false;
	        int tlbsize= Machine.processor().getTLBSize();
	     	//remove invalidated page table entries from TLB
	     	
	     	
	     	//check if tlb is full or not
	     	for(int j=0;j<tlbsize;j++)
	     	{
	     		TranslationEntry tlbentry=Machine.processor().readTLBEntry(j);
	     		if(tlbentry.valid == false)
	     		{
	     			updated=true;
	     			tle.valid=true;
	     			Machine.processor().writeTLBEntry(j, tle);
	     			break;
	     		}
	     	}
	     	if(updated)
	     	  return updated;
	     	else
	     	{
	     		int min_pos=VMKernel.getTLBReplacePosition();
	     		tle.valid=true;
	     		updated=true;
	     		Machine.processor().writeTLBEntry(min_pos, tle);
	     		VMKernel.setNewTLBEntry(min_pos);
	     	}	
	     	return updated;	     	
	     	
	     	
	}
	
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		// Invalid virtual address return 0 or if length to be read is 0
		if (vaddr < 0 || length == 0)
			return 0;
		
		byte[] memory = Machine.processor().getMemory();
		
		int noOfPagesToRead = length/pageSize;
		
		int vOffset = findvoffset(vaddr);
		
		int vpn = vaddrtovpn(vaddr);
		
		if(vOffset > 0)
			noOfPagesToRead++;
		
		int amountRead = 0;
		
		/* Reading pages one by one*/
		while(noOfPagesToRead > 0)
		{
			int paddr= VMKernel.getPPN(pid, vpn) + vOffset;
			
			if (paddr >= memory.length)
				return amountRead;
			
			int lengthToRead = Math.min(pageSize - vOffset, length - amountRead);
			
			System.arraycopy(memory, paddr, data, offset, lengthToRead);
			
			amountRead += lengthToRead;
			offset += lengthToRead;
			
			vOffset = 0;
			vpn++;
			
			noOfPagesToRead--;
		}

		return amountRead;
	}
	
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		// Invalid virtual address return 0 or if length to be read is 0
		if (vaddr < 0 || length == 0)
			return 0;
		
		byte[] memory = Machine.processor().getMemory();
		
		int noOfPagesToWrite = length/pageSize;
		
		int vOffset = findvoffset(vaddr);
		
		int vpn = vaddrtovpn(vaddr);
		
		if(vOffset > 0)
			noOfPagesToWrite++;
		
		int amountWritten = 0;
		
		/* Writing pages one by one*/
		while(noOfPagesToWrite > 0)
		{
			int paddr= VMKernel.getPPN(pid, vpn) + vOffset;
			
			/* Should also check for read only pages*/
			if (paddr >= memory.length)
				return amountWritten;
			
			int lengthToWrite = Math.min(pageSize - vOffset, length - amountWritten);
			
			System.arraycopy(data, offset, memory, paddr, lengthToWrite);
			
			amountWritten += lengthToWrite;
			offset += lengthToWrite;
			
			vOffset = 0;
			vpn++;
			
			noOfPagesToWrite--;
		}

		return amountWritten;
	}
	
	private int findvoffset(int vaddr)
    {
    	int voffset=vaddr % pageSize;
		return voffset;
    }
	
	private int vaddrtovpn(int vaddr)
	{
		int vpn=vaddr/pageSize;
		return vpn;
	}

	private void handleTLBMiss(int virtAddress){
		//get from pageTable
		//add it to tlb using a replacement policy   
	     	//if tlb is full, insert using a replacement algorithm
	            	
	     	
	     		
	     	
	}
	
	private TranslationEntry getEntryFromPageTable(int virtAddr){
		//get the entry from inverted page table
		TranslationEntry entry = null;
		
		//case 1: PageTable hit
		//case 2: PageTable miss
		
		return entry;
	}
	
	private static final int pageSize = Processor.pageSize;
	private int initialPC, initialSP;
	private int argc, argv;
	
	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
	
	private int pid = -1;
}
