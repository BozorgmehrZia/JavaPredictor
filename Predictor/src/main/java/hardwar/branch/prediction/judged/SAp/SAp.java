package hardwar.branch.prediction.judged.SAp;


import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class SAp implements BranchPredictor {

    private final int branchInstructionSize;
    private final int KSize;
    private final ShiftRegister SC;
    private final RegisterBank PSBHR; // per set branch history register
    private final Cache<Bit[], Bit[]> PAPHT; // per address predication history table

    public SAp() {
        this(4, 2, 8, 4);
    }

    public SAp(int BHRSize, int SCSize, int branchInstructionSize, int KSize) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;

        // Initialize the PSBHR with the given bhr and Ksize
        PSBHR = new RegisterBank(KSize, BHRSize);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PAPHT = new PerAddressPredictionHistoryTable(branchInstructionSize, 1 << BHRSize, SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SC", SCSize, null);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        // TODO: complete Task 1
        Bit[] jumpAddress = branchInstruction.getInstructionAddress();


        // Read the associated block with the BHR value
        ShiftRegister shiftRegister = PSBHR.read(getRBAddressLine(jumpAddress));
        Bit[] cacheBlock = PAPHT.setDefault(getCacheEntry(jumpAddress, shiftRegister.read()), getDefaultBlock());


        // Load the read block from the cache into the SC register
        SC.load(cacheBlock);

        // Return the MSB of the read block or SC register
        return BranchResult.of(SC.read()[0] == Bit.ONE);
    }

    @Override
    public void update(BranchInstruction branchInstruction, BranchResult actual) {
        // TODO:complete Task 2
        Bit[] scBits = SC.read();
        Bit[] count = CombinationalLogic.count(scBits, actual == BranchResult.TAKEN, CountMode.SATURATING);

        Bit[] instructionAddress = branchInstruction.getInstructionAddress();


        // Save the updated value into the cache via BHR
        PAPHT.put(getCacheEntry(instructionAddress, PSBHR.read(getRBAddressLine(instructionAddress)).read()), count);

        // Update the BHR with the actual branch result
        ShiftRegister read = PSBHR.read(getRBAddressLine(instructionAddress));
        read.insert(Bit.of(actual == BranchResult.TAKEN));
        PSBHR.write(getRBAddressLine(instructionAddress), read.read());
    }


    private Bit[] getRBAddressLine(Bit[] branchAddress) {
        // hash the branch address
        return hash(branchAddress);
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, branchInstructionSize);
        System.arraycopy(BHRValue, 0, cacheEntry, branchAddress.length, BHRValue.length);
        return cacheEntry;
    }


    /**
     * hash N bits to a K bit value
     *
     * @param bits program counter
     * @return hash value of fist M bits of `bits` in K bits
     */
    private Bit[] hash(Bit[] bits) {
        Bit[] hash = new Bit[KSize];

        // XOR the first M bits of the PC to produce the hash
        for (int i = 0; i < branchInstructionSize; i++) {
            int j = i % KSize;
            if (hash[j] == null) {
                hash[j] = bits[i];
            } else {
                Bit xorProduce = hash[j].getValue() ^ bits[i].getValue() ? Bit.ONE : Bit.ZERO;
                hash[j] = xorProduce;

            }
        }
        return hash;
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
