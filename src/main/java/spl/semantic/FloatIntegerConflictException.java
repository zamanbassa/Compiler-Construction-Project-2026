package spl.semantic;

public final class FloatIntegerConflictException extends RuntimeException
{
    public static final String MESSAGE = "A float-integer-conflict might perhaps be possible";

    public FloatIntegerConflictException()
    {
        super(MESSAGE);
    }
}