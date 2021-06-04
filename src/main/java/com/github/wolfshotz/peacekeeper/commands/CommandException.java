package com.github.wolfshotz.peacekeeper.commands;

public class CommandException extends RuntimeException
{
    public CommandException(String s)
    {
        super(s);
    }

    public CommandException(String s, Throwable t)
    {
        super(s, t);
    }
}
