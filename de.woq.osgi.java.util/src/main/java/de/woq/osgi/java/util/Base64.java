/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.osgi.java.util;

public final class Base64
{

  private Base64()
  {
  }

  public static String encode(byte[] raw)
  {
    StringBuffer encoded = new StringBuffer();
    for (int i = 0; i < raw.length; i += 3)
    {
      encoded.append(encodeBlock(raw, i));
    }
    return encoded.toString();
  }

  public static byte[] decode(String encoded)
  {
    int pad = 0;

    for (int i = encoded.length() - 1; encoded.charAt(i) == '='; i--)
    {
      pad++;
    }

    int length = encoded.length() * 6 / 8 - pad;
    byte[] result = new byte[length];

    int rawIndex = 0;

    for (int i = 0; i < encoded.length(); i += 4)
    {
      int block =
       (getValue(encoded.charAt(i)) << 18) +
        (getValue(encoded.charAt(i + 1)) << 12) +
        (getValue(encoded.charAt(i + 2)) << 6) +
        getValue(encoded.charAt(i + 3));

      for (int j = 0; j < 3 && rawIndex + j < result.length; j++)
      {
        result[rawIndex + j] = (byte) (block >> (8 * (2 - j)) & 0xff);
      }
      rawIndex += 3;
    }
    return result;
  }

  private static char[] encodeBlock(byte[] raw, int offset)
  {
    int block = 0;
    int slack = raw.length - offset - 1;
    int end = (slack >= 2) ? 2 : slack;

    for (int i = 0; i <= end; i++)
    {
      byte b = raw[offset + i];
      int value = (b < 0) ? b + 256 : b;
      block += value << (8 * (2 - i));
    }

    char[] result = new char[4];

    for (int i = 0; i < 4; i++)
    {
      int sixBit = (block >>> (6 * (3 - i))) & 0x3f;
      result[i] = getChar(sixBit);
    }

    if (slack < 1)
    {
      result[2] = '=';
    }
    if (slack < 2)
    {
      result[3] = '=';
    }
    return result;
  }

  private static char getChar(int sixBit)
  {
    if (sixBit >= 0 && sixBit <= 25)
    {
      return (char) ('A' + sixBit);
    }
    if (sixBit >= 26 && sixBit <= 51)
    {
      return (char) ('a' + sixBit - 26);
    }
    if (sixBit >= 52 && sixBit <= 61)
    {
      return (char) ('0' + sixBit - 52);
    }
    if (sixBit == 62)
    {
      return '+';
    }
    if (sixBit == 63)
    {
      return '/';
    }
    return '?';
  }

  private static int getValue(char c)
  {
    if (c >= 'A' && c <= 'Z')
    {
      return c - 'A';
    }
    if (c >= 'a' && c <= 'z')
    {
      return c - 'a' + 26;
    }
    if (c >= '0' && c <= '9')
    {
      return c - '0' + 52;
    }
    if (c == '+')
    {
      return 62;
    }
    if (c == '/')
    {
      return 63;
    }
    if (c == '=')
    {
      return 0;
    }
    return -1;
  }

}
