package mrl_2021.comm;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.StandardMessage;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.communication.CommunicationMessage;
import adf.component.communication.MessageCoordinator;
import rescuecore2.standard.entities.StandardEntityURN;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static rescuecore2.standard.entities.StandardEntityURN.*;

public class MMessageCoordinator extends MessageCoordinator
{
    private static final StandardEntityURN FB = FIRE_BRIGADE;
    private static final StandardEntityURN AT = AMBULANCE_TEAM;
    private static final StandardEntityURN PF = POLICE_FORCE;

    @Override
    public void coordinate(
        AgentInfo ai, WorldInfo wi, ScenarioInfo si, MessageManager mm,
        ArrayList<CommunicationMessage> messages, List<List<CommunicationMessage>> result)
    {
        final List<StandardMessage> standards =
            this.gatherStandardMessages(messages);

        List<Channel> channels = new LinkedList<>();
        channels.add(new VoiceCommunication(new int[]{0}));
        channels.add(new ChannelFB(ai, this.gatherChannels(FB, si)));
        channels.add(new ChannelAT(ai, this.gatherChannels(AT, si)));
        channels.add(new ChannelPF(ai, this.gatherChannels(PF, si)));

        for (Channel channel : channels)
            for (StandardMessage standard : standards)
                channel.addWithFilter(standard);

        for (Channel channel : channels)
        {
            channel.sort();
            this.assignMessagesOnResult(channel, result, si);
        }
    }

    private static List<StandardMessage> gatherStandardMessages(
        List<CommunicationMessage> messages)
    {
        final Stream<StandardMessage> ret = messages
            .stream()
            .filter(StandardMessage.class::isInstance)
            .map(StandardMessage.class::cast);

        return ret.collect(toList());
    }

    private static int[] gatherChannels(StandardEntityURN type, ScenarioInfo si)
    {
        final int num = si.getCommsChannelsCount()-1;
        final int max = computeMaxChannels(type, si);

        final IntStream ret = IntStream.range(0, max)
            .map(i -> MChannelSubscriber.assignChannel(type, i, num));

        return ret.toArray();
    }

    private static int computeMaxChannels(
        StandardEntityURN type, ScenarioInfo si)
    {
        final int np = si.getCommsChannelsMaxPlatoon();
        final int no = si.getCommsChannelsMaxOffice();
        return MChannelSubscriber.computeMaxChannels(type, np, no);
    }

    private static void assignMessagesOnResult(
        Channel channel,
        List<List<CommunicationMessage>> result,
        ScenarioInfo si)
    {
        final int[] nums = channel.getNumbers();
        final int n = nums.length;
        int[] bandwidths =
            IntStream.of(nums).map(i -> gainBandwidth(i, si)).toArray();

        for (StandardMessage message : channel.getMessages())
        {
            final int bytes = message.getByteArraySize();
            final int index = seekBestChannel(bytes, bandwidths);
            if (index < 0) break;

            bandwidths[index] -= bytes;
            result.get(nums[index]).add(message);
        }
    }

    private static int gainBandwidth(int index, ScenarioInfo si)
    {
        if (index == 0) return si.getVoiceMessagesSize();
        return si.getCommsChannelBandwidth(index);
    }

    private static int seekBestChannel(int size, int[] bandwidths)
    {
        final int n = bandwidths.length;
        final int index = IntStream.range(0, n)
            .boxed().max(comparingInt(i -> bandwidths[i])).orElse(-1);

        return index < 0 || bandwidths[index] < size ? -1 : index;
    }
}
