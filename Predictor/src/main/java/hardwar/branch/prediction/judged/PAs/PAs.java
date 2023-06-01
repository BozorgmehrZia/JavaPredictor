package hardwar.branch.prediction.judged.PAs;


import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAs implements BranchPredictor {

    private final int branchInstructionSize;
    private final int KSize;
    private final HashMode hashMode;
    private final ShiftRegister SC; // saturating counter register
    private final RegisterBank PABHR; // per address Branch History Register
    private final Cache<Bit[], Bit[]> PSPHT; // Per Set Predication History Table

    public PAs() {
        this(4, 2, 8, 4, HashMode.XOR);
    }

    public PAs(int BHRSize, int SCSize, int branchInstructionSize, int KSize, HashMode hashMode) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;
        this.hashMode = HashMode.XOR;

        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(BHRSize, branchInstructionSize);

        // Initializing the PAPHT with K bit as PHT selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PSPHT = new PerAddressPredictionHistoryTable(KSize, 1 << BHRSize, SCSize);

        // Initialize the saturating counter
        SC = new SIPORegister("SC", SCSize, null);
    }

    /**
     * predicts the result of a branch instruction based on the per address BHR and hash value of branch
     * instruction address
     *
     * @param branchInstruction the branch instruction
     * @return the predicted outcome of the branch instruction (taken or not taken)
     */
    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        // TODO: complete Task 1
        Bit[] jumpAddress = branchInstruction.getInstructionAddress();

        // Read the associated block with the BHR value
        ShiftRegister shiftRegister = PABHR.read(jumpAddress);
        CombinationalLogic.hash(jumpAddress, KSize, HashMode.XOR);
        Bit[] cacheEntry = getCacheEntry(jumpAddress, shiftRegister.read());
        Bit[] cacheBlock = PSPHT.setDefault(cacheEntry, getDefaultBlock());


        // Load the read block from the cache into the SC register
        SC.load(cacheBlock);

        // Return the MSB of the read block or SC register
        return BranchResult.of(SC.read()[0] == Bit.ONE);
    }

    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        // TODO:complete Task 2
        Bit[] scBits = SC.read();
        Bit[] count = CombinationalLogic.count(scBits, actual == BranchResult.TAKEN, CountMode.SATURATING);

        Bit[] instructionAddress = instruction.getInstructionAddress();


        // Save the updated value into the cache via BHR
        PSPHT.put(getCacheEntry(instructionAddress, PABHR.read(instructionAddress).read()), count);

        // Update the BHR with the actual branch result
        ShiftRegister read = PABHR.read(instructionAddress);
        read.insert(Bit.of(actual == BranchResult.TAKEN));
        PABHR.write(instructionAddress, read.read());
    }

    @Override
    public String monitor() {
        return "PAs predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PSPHT.monitor();
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // hash the branch address
        Bit[] hashKSize = CombinationalLogic.hash(branchAddress, KSize, hashMode);

        // Concatenate the Hash bits with the BHR bits
        Bit[] cacheEntry = new Bit[hashKSize.length + BHRValue.length];
        System.arraycopy(hashKSize, 0, cacheEntry, 0, hashKSize.length);
        System.arraycopy(BHRValue, 0, cacheEntry, hashKSize.length, BHRValue.length);

        return cacheEntry;
    }


    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }
}
