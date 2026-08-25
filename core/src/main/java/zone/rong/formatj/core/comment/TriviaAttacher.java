package zone.rong.formatj.core.comment;

import zone.rong.formatj.core.lexer.Token;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides which token each comment belongs to.
 *
 * <p>Comment attachment is where most Java formatters go wrong: a comment is not part of the grammar,
 * so nothing in the tree says whether {@code // why} above a field explains the field, closes off the
 * one before it, or belongs to nothing at all. FormatJ makes the choice explicit here rather than
 * letting it fall out of the parser.
 *
 * <p>Not yet implemented; the classification below is the contract the emitter will consume.
 */
public final class TriviaAttacher {

    /** Where an attached comment sits relative to the token that owns it. */
    public enum Attachment {

        /** On its own line or lines, before the owning token. */
        LEADING,
        /** After the owning token, on the same line. */
        TRAILING,
        /** Inside an otherwise empty construct, owned by nothing in particular. */
        DANGLING

    }

    /**
     * One comment and its placement.
     *
     * @param comment the comment token
     * @param ownerIndex index in the token list of the token the comment attaches to
     * @param attachment how it relates to that token
     */
    public record AttachedComment(Token comment, int ownerIndex, Attachment attachment) { }

    private TriviaAttacher() { }

    /** Classifies every comment in a token stream. */
    public static List<AttachedComment> attach(List<Token> tokens) {
        // TODO: real attachment rules, driven by the comments.* options.
        return List.copyOf(new ArrayList<AttachedComment>());
    }

}
