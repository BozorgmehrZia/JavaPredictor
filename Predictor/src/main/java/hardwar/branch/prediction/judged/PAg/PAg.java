package hardwar.branch.prediction.judged.PAg;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAg implements BranchPredictor {
    private final ShiftRegister SC; // saturating counter register
    private final RegisterBank PABHR; // per address branch history register
    private final Cache<Bit[], Bit[]> PHT; // page history table

    public PAg() {
        this(4, 2, 8);
    }

    /**
     * Creates a new PAg predictor with the given BHR register size and initializes the PABHR based on
     * the branch instruction size and BHR size
     *
     * @param BHRSize               the size of the BHR register
     * @param SCSize                the size of the register which hold the saturating counter value
     * @param branchInstructionSize the number of bits which is used for saving a branch instruction
     */
    public PAg(int BHRSize, int SCSize, int branchInstructionSize) {
        // TODO: complete the constructor
        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initialize the PHT with a size of 2^size and each entry having a saturating counter of size "SCSize"
        PHT = new PageHistoryTable(1 << BHRSize, SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SC", SCSize, null);
    }

    /**
     * @param instruction the branch instruction
     * @return the predicted outcome of the branch instruction (taken or not taken)
     */
    @Override
    public BranchResult predict(BranchInstruction instruction) {
        // TODO: complete Task 1
        Bit[] jumpAddress = instruction.getInstructionAddress();
        // Read the associated block with the BHR value
        ShiftRegister shiftRegister = PABHR.read(jumpAddress);
        Bit[] cacheBlock = PHT.setDefault(shiftRegister.read(), getDefaultBlock());


        // Load the read block from the cache into the SC register
        SC.load(cacheBlock);

        // Return the MSB of the read block or SC register
        return BranchResult.of(SC.read()[0] == Bit.ONE);
    }

    /**
     * @param instruction the branch instruction
     * @param actual      the actual result of branch (taken or not)
     */
    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        // TODO: complete Task 2
        Bit[] scBits = SC.read();
        Bit[] count = CombinationalLogic.count(scBits, actual == BranchResult.TAKEN, CountMode.SATURATING);

        Bit[] instructionAddress = instruction.getInstructionAddress();
        

        // Save the updated value into the cache via BHR
        PHT.put(PABHR.read(instructionAddress).read(), count);

        // Update the BHR with the actual branch result
        ShiftRegister read = PABHR.read(instructionAddress);
        read.insert(Bit.of(actual == BranchResult.TAKEN));
        PABHR.write(instructionAddress, read.read());
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
        return "PAg predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PHT.monitor();
    }
}
