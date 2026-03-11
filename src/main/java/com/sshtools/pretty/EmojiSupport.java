package com.sshtools.pretty;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gluonhq.emoji.Emoji;
import com.gluonhq.emoji.EmojiData;
import com.gluonhq.emoji.util.EmojiImageUtils;
import com.sshtools.terminal.emulation.VDUCharacterCanvas;
import com.sshtools.terminal.emulation.fonts.FontManager;
import com.sshtools.terminal.emulation.fonts.SoftCell;
import com.sshtools.terminal.emulation.fonts.SoftFont;
import com.sshtools.terminal.emulation.images.ImageSupport;

import javafx.scene.image.Image;
import javafx.scene.text.Font;

public class EmojiSupport implements SoftFont {
	
	private final static Logger LOG = LoggerFactory.getLogger(EmojiSupport.class);

	private final  Image image;
	private Optional<Emoji> lastEmoji = Optional.empty();
	private int[] lastEmojiCodepoints = new int[0];

	public EmojiSupport(FontManager<Font> fontManager) {
		fontManager.addSoftFont("Emoji", this);
		image = EmojiImageUtils.getImage20();
	}

	@Override
	public int width() {
		return 20;
	}

	@Override
	public int height() {
		return 20;
	}

	@Override
	public List<SoftCell> cells() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(SoftCell cell) {
		throw new UnsupportedOperationException();		
	}

	@Override
	public int getStartCodepoint() {
		return 0;
	}

	@Override
	public int getEndCodepoint() {
		return Integer.MAX_VALUE;
	}

	@Override
	public void removeCellsOfWidth(int pcmw) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean inSoftFont(int c) {
		return emoji(c).isPresent();
	}

	private Optional<Emoji> emoji(int... c) {
		if(Arrays.equals(c, lastEmojiCodepoints)) {
			return lastEmoji;
		}
		
		Optional<Emoji> res;
		if(c.length == 1) {
			res = EmojiData.emojiFromCodepoints(Integer.toHexString(c[0]));
			if(LOG.isTraceEnabled()) {
				LOG.trace("Lookup of " + c[0] + " (" + (char)c[0] + ") = " + res.isPresent());
			}
			return res;
		} else {
			/* TODO the big one .. unicode sequences .. idk how tf thats going to work! */
			res = Optional.empty();
		}
		lastEmojiCodepoints = c;
		lastEmoji = res;
		return res;
	}

	@Override
	public SoftCell get(int codepoint) {
		var emojiOr = emoji(codepoint);
		if(emojiOr.isPresent()) {
			var emoji = emojiOr.get();
			/* TODO widths */
			return new SoftCell(codepoint, 20, 20);
		}
		else
			return null;
	}

	@Override
	public <I> void drawChar(VDUCharacterCanvas<I> g, int c, int x, int y, int cw, int ch,
			ImageSupport<I> imageSupport) {
		var emoji = emoji(c).get();
		var viewport = EmojiImageUtils.getViewportFor20(emoji);
		g.drawImage((I)image, (int)viewport.getMinX(), (int)viewport.getMinY(), (int)viewport.getWidth(), (int)viewport.getHeight(),  x, y, cw * (int)( viewport.getWidth() / 20.0), ch);
	}
}
