package mrl_2021.comm;

import adf.agent.communication.standard.bundle.StandardMessage;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;

import java.util.Arrays;

public class VoiceCommunication extends AbstractChannel
{
    private static final Class[] WHITE_LIST =
    {
        CommandPolice.class
    };

    public VoiceCommunication(int[] numbers)
    {
        super(numbers, false);
    }

    @Override
    protected boolean applyFilter(StandardMessage message)
    {
        if (!super.applyFilter(message)) return false;

        final Class clazz = message.getClass();
        return Arrays.asList(WHITE_LIST).contains(clazz);
    }
}
