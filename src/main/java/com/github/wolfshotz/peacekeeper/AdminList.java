package com.github.wolfshotz.peacekeeper;

import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminList
{
    private static final String ADMINS_URL = "https://raw.githubusercontent.com/WolfShotz/Peacekeeper/master/src/main/resources/admins.txt";
    private static final List<Long> ADMIN_LIST = Collections.synchronizedList(new ArrayList<>());

    public static boolean contains(Member user)
    {
        return contains(user.getId().asLong());
    }

    public static boolean contains(User user)
    {
        return contains(user.getId().asLong());
    }

    public static boolean contains(long id)
    {
        return ADMIN_LIST.contains(id);
    }

    @SuppressWarnings("ConstantConditions")
    public static void collectAdmins()
    {
        ADMIN_LIST.clear();
        InputStreamReader reader;

        try
        {
            reader = new InputStreamReader(new URL(ADMINS_URL).openStream());
        }
        catch (IOException e)
        {
            reader = new InputStreamReader(Main.class.getResourceAsStream("admins.txt"));
        }

        try (BufferedReader pointer = new BufferedReader(reader))
        {
            if (reader == null) throw new NullPointerException("Could not retrieve InputStream for admin list");

            String line;
            while ((line = pointer.readLine()) != null)
                if (!line.startsWith("//")) ADMIN_LIST.add(Long.parseLong(line.substring(0, 18).trim()));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not Load admin list", e);
        }
    }
}
