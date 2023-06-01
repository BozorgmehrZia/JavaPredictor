package hardwar.branch.prediction.judged.SAs;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class SAs implements BranchPredictor {

    private final int branchInstructionSize;
    private final int KSize;
    private final ShiftRegister SC;
    private final RegisterBank PSBHR; // per set branch history register
    private final Cache<Bit[], Bit[]> PSPHT; // per set predication history table
    private final HashMode hashMode;

    public SAs() {
        this(4, 2, 8, 4, HashMode.XOR);
    }

    public SAs(int BHRSize, int SCSize, int branchInstructionSize, int KSize, HashMode hashMode) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;
        this.hashMode = HashMode.XOR;

        // Initialize the PSBHR with the given bhr and branch instruction size
        PSBHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PSPHT = new PerAddressPredictionHistoryTable(branchInstructionSize, 1 << BHRSize, SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SC", SCSize, null);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        // TODO: complete Task 1
        Bit[] jumpAddress = branchInstruction.getInstructionAddress();


        // Read the associated block with the BHR value
        ShiftRegister shiftRegister = PSBHR.read(getAddressLine(jumpAddress));
        Bit[] cacheBlock = PSPHT.setDefault(getCacheEntry(getAddressLine(jumpAddress), shiftRegister.read()), getDefaultBlock());


        // Load the read block from the cache into the SC register
        SC.load(cacheBlock);

        // Return the MSB of the read block or SC register
        return BranchResult.of(SC.read()[0] == Bit.ONE);
    }

    @Override
    public void update(BranchInstruction branchInstruction, BranchResult actual) {
        // TODO: complete Task 2
        Bit[] scBits = SC.read();
        Bit[] count = CombinationalLogic.count(scBits, actual == BranchResult.TAKEN, CountMode.SATURATING);

        Bit[] instructionAddress = branchInstruction.getInstructionAddress();


        // Save the updated value into the cache via BHR
        PSPHT.put(getCacheEntry(getAddressLine(instructionAddress), PSBHR.read(getAddressLine(instructionAddress)).read()), count);

        // Update the BHR with the actual branch result
        ShiftRegister read = PSBHR.read(getAddressLine(instructionAddress));
        read.insert(Bit.of(actual == BranchResult.TAKEN));
        PSBHR.write(getAddressLine(instructionAddress), read.read());
    }


    private Bit[] getAddressLine(Bit[] branchAddress) {
        // hash the branch address
        return CombinationalLogic.hash(branchAddress, KSize, hashMode);
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, KSize);
        System.arraycopy(BHRValue, 0, cacheEntry, branchAddress.length, BHRValue.length);
        return cacheEntry;
    }

    /**
     * @return a zero series of bits as default value of cache block
     */
    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }

    @Override
    public String monitor() {
        return null;
    }
}
