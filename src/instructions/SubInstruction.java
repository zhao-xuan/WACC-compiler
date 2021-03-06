package instructions;

import util.Register;

public class SubInstruction extends Instruction {
    private final Register rDest;
    private final Register lhs;
    private final Operand2 rhs;
    public boolean setFlags;


    public SubInstruction(Register rDest, Register lhs, Operand2 rhs) {
        this.rDest = rDest;
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public String toCode() {
        return "SUB" + (setFlags ? "S " : " ") + rDest + ", " + lhs + ", " + rhs;
    }
}
