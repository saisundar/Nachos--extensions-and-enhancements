package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.ArrayList;
/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		Children = new HashMap<Integer,UserProcess>();
		joinWait=null;
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		
		//Each user process instantiated will reach here. 
		//Create the standard consoles 0 and 1 here 
		
		mutex.acquire();
		
		fdTable.put(0, UserKernel.console.openForReading());
		fdTable.put(1, UserKernel.console.openForWriting());
		
		//Set process id
		this.pid = next_pid++; //1st process has pid 1. This is analogous to init which is responsible to 
		//start and stop the system
		mutex.release();
		
		next_fd = 2;//subsequent fd start from 2
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
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
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}
	
	private int vaddrtovpn(int vaddr)
	{
		int vpn=vaddr/pageSize;
		return vpn;
	}
	
    private int findvoffset(int vaddr)
    {
    	int voffset=vaddr % pageSize;
		return voffset;
    }
    
    private int getPPN(int vpn)
    {
    	return pageTable[vpn].ppn;
    }
    
    private int getpaddr(int ppn, int voffset)
    {
    	return ppn*pageSize + voffset;
    }
    
    private boolean isPageFault(int vaddr, int length)
    {
    	if(vaddr> numPages*pageSize || (vaddr+length) > numPages*pageSize)
    	{
    		return true;
    	}
    	
    	return false;
    }
	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		if(isPageFault(vaddr,length))
		{
			Lib.debug(dbgProcess, "PageFault");	
			return 0;
		}
		
		if(!pageTable[vaddrtovpn(vaddr)].valid)
			return 0;
		
		int paddr= getpaddr(getPPN(vaddrtovpn(vaddr)), findvoffset(vaddr));
		

		int amount = Math.min(length, memory.length - paddr);
		System.arraycopy(memory, paddr, data, offset, amount);

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;
		
		if (pageTable[vaddrtovpn(vaddr)].readOnly || !pageTable[vaddrtovpn(vaddr)].valid)
			return 0;
		
		if (isPageFault(vaddr,length))
		{
			Lib.debug(dbgProcess, "PageFault");
			return 0;
		}
		
		int paddr= getpaddr(getPPN(vaddrtovpn(vaddr)), findvoffset(vaddr));
		int amount = Math.min(length, memory.length - paddr);
		System.arraycopy(data, offset, memory, paddr, amount);


		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

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

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		/* Update the pageTable entries according to what is required for this process*/
		if (!updatePageTable())
			return false;
		
		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		//update pgtable for args
		pageTable[numPages-1].readOnly = true;
		
		return true;
	}

	private boolean updatePageTable(){
		boolean status = true;
		int ppn = 0;
		mutex.acquire();
		if (UserKernel.getNumOfFreePages() < numPages){
			mutex.release();
			return false;
		}
		/*get each vpn and find an available ppn. Add this info to the map*/
		for (int i = 0; i < numPages; i++){
			ppn = UserKernel.getfreepage();
			if (ppn >= 0){
				pageTable[i].ppn = ppn;
				pageTable[i].vpn = i;
				pageTable[i].valid = true;
			} else {
				status = false;
				break;
			}
		}
		mutex.release();
		
		return status;
	}
	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				section.loadPage(i, getPPN(vpn));
				pageTable[vpn].readOnly = section.isReadOnly();
				pageTable[vpn].valid = true;
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
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
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		//This can be invoked only by root process.
		if (this.pid == 1) //init pid is 1
			Machine.halt();

		Lib.debug(dbgProcess, "Machine.halt() did not halt machine!"); //cannot assert here
		return 0;
	}

	//int creat(char *name);
	private int handleCreate(int a0){
		int status = EERR;
		
		if (fdTable.size() == MAX_FD)
			return EMAXFD;
		
		String fName = readVirtualMemoryString(a0, MAX_STRING_LEN);
		if ((null == fName) || (fName.isEmpty())){
			return EINVAL;
		}
		
		OpenFile file = ThreadedKernel.fileSystem.open(fName, true);
		if (null != file){
			fdTable.put(next_fd, file);
			status = next_fd++;
		}
		
		return status;
	}
	//int open(char *name);
	private int handleOpen(int a0){
		int status = EERR;
		
		if (fdTable.size() == MAX_FD)
			return EMAXFD;
		
		String fName = readVirtualMemoryString(a0, MAX_STRING_LEN);
		if ((null == fName) || (fName.isEmpty())){
			return EINVAL;
		}
		
		OpenFile file = ThreadedKernel.fileSystem.open(fName, false);
		if (null != file){
			fdTable.put(next_fd, file);
			status = next_fd++;
		}
		
		return status;
	}
	
	/**
	 * Attempt to read up to count bytes into buffer from the file or stream
	 * referred to by fileDescriptor.
	 *
	 * On success, the number of bytes read is returned. If the file descriptor
	 * refers to a file on disk, the file position is advanced by this number.
	 *
	 * It is not necessarily an error if this number is smaller than the number of
	 * bytes requested. If the file descriptor refers to a file on disk, this
	 * indicates that the end of the file has been reached. If the file descriptor
	 * refers to a stream, this indicates that the fewer bytes are actually
	 * available right now than were requested, but more bytes may become available
	 * in the future. Note that read() never waits for a stream to have more data;
	 * it always returns as much as possible immediately.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is read-only or
	 * invalid, or if a network stream has been terminated by the remote host and
	 * no more data is available.
	 */
	//int read(int fileDescriptor, void *buffer, int count);
	private int handleRead(int a0, int a1, int a2){
		int status = EERR;
		int readLen = a2;
		int fd = a0;
		
		if (!fdTable.containsKey(fd))
			return EINVAL;
		
		OpenFile file = fdTable.get(fd);

		if (null != file){
			byte[] buffer = new byte[readLen];
			status = file.read(buffer, 0, readLen);
			if (status > 0){
				status = writeVirtualMemory(a1, buffer, 0, status);
				//status = writeVirtualMemory(a1, buffer);
			}
		}
		
		return status;
	}
	
	/**
	 * Attempt to write up to count bytes from buffer to the file or stream
	 * referred to by fileDescriptor. write() can return before the bytes are
	 * actually flushed to the file or stream. A write to a stream can block,
	 * however, if kernel queues are temporarily full.
	 *
	 * On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number. It IS an
	 * error if this number is smaller than the number of bytes requested. For
	 * disk files, this indicates that the disk is full. For streams, this
	 * indicates the stream was terminated by the remote host before all the data
	 * was transferred.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
	 * if a network stream has already been terminated by the remote host.
	 */
	//int write(int fileDescriptor, void *buffer, int count);
	private int handleWrite(int a0, int a1, int a2){

		int status = EERR;
		int writeLen = a2;
		int fd = a0;
		
		if (!fdTable.containsKey(fd))
			return EINVAL;
		
		OpenFile file = fdTable.get(fd);

		if (null != file){
			byte[] buffer = new byte[writeLen];
			status = readVirtualMemory(a1, buffer);
			if (status != writeLen){
				return EFULL;
			}
			
			status = file.write(buffer, 0, writeLen);
			if (status != writeLen){
				return EFULL;
			}
		}
		
		return status;
	
	}
	
	
	//int unlink(char *name);
	private int handleUnlink(int a0){
		int status = EERR;
				
		String fName = readVirtualMemoryString(a0, MAX_STRING_LEN);
		if ((null == fName) || (fName.isEmpty())){
			return EINVAL;
		}
		
		/*
		 * If any processes still have the file open, the file will remain in existence
		 * until the last file descriptor referring to it is closed. However, creat()
		 * and open() will not be able to return new file descriptors for the file
		 * until it is deleted.
		 * 
		 * The above is expected as per user API definition. How to iterate throu all processes fdTable
		 * to check if this file is opened or not before deleting.
		 * */
		if (ThreadedKernel.fileSystem.remove(fName)){
			status = SUCCESS;
		}
		
		return status;
	}
	
	//int close(int fileDescriptor);
	private int handleClose(int a0){
		int status = EERR;
		
		//a0 is the fileDescriptor
		OpenFile file = fdTable.get(a0);
		if (null != file){
			file.close();
			fdTable.remove(a0);
			status = SUCCESS;
		}
		
		return status;
	}
	private void setJoinWait()
	{
		joinWait=(UThread) KThread.currentThread();
	}
	private void setParent(UserProcess obj)
	{
		parent=obj;
	}
	private void remChild(int pid)
	{
		Children.remove(pid);
	}
	private void setjoinReturn(int retValue)
	{
		joinReturn=retValue;
	}
		
	//--------------------EXIT----------------------------------------------->
	/**
	 * Terminate the current process immediately. Any open file descriptors
	 * belonging to the process are closed. Any children of the process no longer
	 * have a parent process.
	 *
	 * status is returned to the parent process as this process's exit status and
	 * can be collected using the join syscall. A process exiting normally should
	 * (but is not required to) set status to 0.
	 *
	 * exit() never returns.
	 */
	//void exit(int status);
	private int handleExit(int a0){
		int status = EERR;
		Lib.debug(dbgProcess, "System call Exit invoked");
		
		//loop through and close all current fd
		for (int fd : fdTable.keySet()){
			OpenFile f = fdTable.get(fd);
			f.close();
		}
		
		next_fd = -1;
		fdTable = null;
		
		mutex.acquire();
		/*To release the physical pages allocated*/
		for (int i = 0; i < numPages; i++){
			if(pageTable[i].valid)
			UserKernel.setfreepage(pageTable[i].ppn);
		}
		mutex.release();
		
		numPages = 0;
		pageTable = null;
		
		if(joinWait!=null)
		{	
			boolean intStatus = Machine.interrupt().disable();
			joinWait.ready();
			Machine.interrupt().restore(intStatus);
			joinWait=null;
			if(parent!=null){
				//set join return status based on exception value returned
				if (null != unhandledExceptionName){
					Lib.debug(dbgProcess, "Process terminated due to " + unhandledExceptionName);
				}
				parent.setjoinReturn(a0);
			}
		}
		
		if(parent!=null)
		{
			parent.remChild(pid);
			parent=null;
		}
		ArrayList<UserProcess> children =new ArrayList<UserProcess>(Children.values());
		for(UserProcess child:children)
		{
			child.parent=null;//made an orphan
			child.joinWait = null;
		}
		Children.clear();
		
		if (this.pid == 1){
			Kernel.kernel.terminate();
		} else {
			KThread.currentThread().finish();			
		}
		
		return status;
	}
	//--------------------EXEC----------------------------------------------->
	/**
	 * Execute the program stored in the specified file, with the specified
	 * arguments, in a new child process. The child process has a new unique
	 * process ID, and starts with stdin opened as file descriptor 0, and stdout
	 * opened as file descriptor 1.
	 *
	 * file is a null-terminated string that specifies the name of the file
	 * containing the executable. Note that this string must include the ".coff"
	 * extension.
	 *
	 * argc specifies the number of arguments to pass to the child process. This
	 * number must be non-negative.
	 *
	 * argv is an array of pointers to null-terminated strings that represent the
	 * arguments to pass to the child process. argv[0] points to the first
	 * argument, and argv[argc-1] points to the last argument.
	 *
	 * exec() returns the child process's process ID, which can be passed to
	 * join(). On error, returns -1.
	 */
	//int exec(char *file, int argc, char *argv[]);
	private int handleExec(int fileStringAddress,int argc,int argvStartAddress){
		int status = EERR,addressi=0;
		String processName=null;
		Lib.debug(dbgProcess, "System call EXEC invoked");

		String[] args=null;
		if(fileStringAddress==0)
			return status;
		
		processName = readVirtualMemoryString(fileStringAddress,256);
		
		if(argc>0)
		{
			args = new String[argc];
			byte[] argi = new byte[4];
			for (int i=0; i<argc; i++)
			{
				readVirtualMemory(argvStartAddress+i*4, argi);
				addressi=Lib.bytesToInt(argi,0);
				args[i] = readVirtualMemoryString(addressi, 256);
			}
		}
		UserProcess child = new UserProcess();

		if(child.execute(processName, args)==false)
			return status;

		Children.put(child.pid,child);
		child.setParent(this);
		
		return child.pid;
	}
	
	//--------------------JOIN----------------------------------------------->
	/**
	 * Suspend execution of the current process until the child process specified
	 * by the processID argument has exited. If the child has already exited by the
	 * time of the call, returns immediately. When the current process resumes, it
	 * disowns the child process, so that join() cannot be used on that process
	 * again.
	 *
	 * processID is the process ID of the child process, returned by exec().
	 *
	 * status points to an integer where the exit status of the child process will
	 * be stored. This is the value the child passed to exit(). If the child exited
	 * because of an unhandled exception, the value stored is not defined.
	 *
	 * If the child exited normally, returns 1. If the child exited as a result of
	 * an unhandled exception, returns 0. If processID does not refer to a child
	 * process of the current process, returns -1.
	 * */
	//int join(int processID, int *status);
	private int handleJoin(int pid,int statusAddr){
		int status = EERR;
		Lib.debug(dbgProcess, "System call JOIN invoked");
		
		if(!Children.containsKey(pid))
			return status;
		
		Children.get(pid).setJoinWait();         //add the current Kthread/Uthread to the jin attribyte in child.
		boolean intStatus = Machine.interrupt().disable();
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
		//will wake up here after the child process has exited
		byte[] returnVal=new byte[4];
		Lib.bytesFromInt(returnVal,0, joinReturn);
		writeVirtualMemory( statusAddr, returnVal);	
		
		if(joinReturn==0)
			return 1;
		return 0;
		
	}
	
	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();

		case syscallCreate:
			return handleCreate(a0);
	
		case syscallOpen:
			return handleOpen(a0);
	
		case syscallRead:
			return handleRead(a0, a1, a2);

		case syscallWrite:
			return handleWrite(a0, a1, a2);

		case syscallClose:
			return handleClose(a0);
			
		case syscallUnlink:
			return handleUnlink(a0);
		
		case syscallExit:
			return handleExit(a0);
			
		case syscallExec:
			return handleExec(a0,a1,a2);
			
		case syscallJoin:
			return handleJoin(a0,a1);
			

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			unhandledExceptionName = Processor.exceptionNames[0];
			handleExit(1);
			//Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			unhandledExceptionName = Processor.exceptionNames[cause];
			handleExit(1);
			//Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
	private static final int MAX_STRING_LEN = 256;
	
	private HashMap<Integer, OpenFile> fdTable = new HashMap<Integer, OpenFile>();
	//next_fd will be incremental. So we are not reusing the fd id.
	//not handling case when the value overflows
	private int next_fd = 0; //0 and 1 are for standard console
	private int MAX_FD = 16;
	private UThread joinWait;
	private int pid = -1;
	private String unhandledExceptionName = null; //to track an exception that cannot be handled
	private static int next_pid = 1;
	private HashMap<Integer,UserProcess> Children;
	private UserProcess parent;
	private int joinReturn;
	private static final int SUCCESS = 0;
	//File error codes
	//Nachos is expecting -1 for error. So changing error code value
	private static final int EERR = -1;
	private static final int EEXIST = -1;
	private static final int EMAXFD = -1;
	private static final int EINVAL = -1;
	private static final int EFULL = -1;
	
	private Lock mutex = new Lock();
}
