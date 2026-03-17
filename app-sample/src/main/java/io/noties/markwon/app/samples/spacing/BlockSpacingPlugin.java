package io.noties.markwon.app.samples.spacing;

import android.content.Context;

import androidx.annotation.NonNull;

import org.commonmark.node.BulletList;
import org.commonmark.node.Heading;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.core.CoreProps;
import io.noties.markwon.core.spans.LastLineSpacingSpan;

/**
 * Adds fixed vertical spacing between blocks:
 * - Paragraph <-> Paragraph
 * - Heading <-> Paragraph
 * - List (ordered/unordered) <-> Paragraph/Heading
 * - ListItem <-> ListItem (within the same list)
 *
 * Uses {@link LastLineSpacingSpan} to add extra height after the last line of a block.
 */
public class BlockSpacingPlugin extends AbstractMarkwonPlugin {

    @NonNull
    public static Markwon create(@NonNull Context context, float spacingDp) {
        final int spacingPx = (int) (spacingDp * context.getResources().getDisplayMetrics().density + .5F);
        return Markwon.builder(context)
                .usePlugin(new BlockSpacingPlugin(spacingPx))
                .build();
    }

    private final int spacingPx;

    public BlockSpacingPlugin(int spacingPx) {
        this.spacingPx = spacingPx;
    }

    @Override
    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
        paragraph(builder);
        heading(builder);
        bulletList(builder);
        orderedList(builder);
        listItem(builder);
    }

    private void paragraph(@NonNull MarkwonVisitor.Builder builder) {
        builder.on(Paragraph.class, new MarkwonVisitor.NodeVisitor<Paragraph>() {
            @Override
            public void visit(@NonNull MarkwonVisitor visitor, @NonNull Paragraph paragraph) {

                final boolean inTightList = isInTightList(paragraph);

                if (!inTightList) {
                    visitor.blockStart(paragraph);
                }

                final int start = visitor.length();
                visitor.visitChildren(paragraph);

                CoreProps.PARAGRAPH_IS_IN_TIGHT_LIST.set(visitor.renderProps(), inTightList);
                visitor.setSpansForNodeOptional(paragraph, start);

                if (!inTightList) {
                    final Node next = paragraph.getNext();
                    if (isSpacingTargetAfterParagraphOrHeading(next)) {
                        visitor.setSpans(start, LastLineSpacingSpan.create(spacingPx));
                    }
                    visitor.blockEnd(paragraph);
                }
            }

            private boolean isInTightList(@NonNull Paragraph paragraph) {
                final Node parent = paragraph.getParent();
                if (parent != null) {
                    final Node gramps = parent.getParent();
                    if (gramps instanceof ListBlock) {
                        return ((ListBlock) gramps).isTight();
                    }
                }
                return false;
            }
        });
    }

    private void heading(@NonNull MarkwonVisitor.Builder builder) {
        builder.on(Heading.class, new MarkwonVisitor.NodeVisitor<Heading>() {
            @Override
            public void visit(@NonNull MarkwonVisitor visitor, @NonNull Heading heading) {

                visitor.blockStart(heading);

                final int start = visitor.length();
                visitor.visitChildren(heading);

                CoreProps.HEADING_LEVEL.set(visitor.renderProps(), heading.getLevel());
                visitor.setSpansForNodeOptional(heading, start);

                final Node next = heading.getNext();
                if (isSpacingTargetAfterParagraphOrHeading(next)) {
                    visitor.setSpans(start, LastLineSpacingSpan.create(spacingPx));
                }

                visitor.blockEnd(heading);
            }
        });
    }

    private void bulletList(@NonNull MarkwonVisitor.Builder builder) {
        builder.on(BulletList.class, new MarkwonVisitor.NodeVisitor<BulletList>() {
            @Override
            public void visit(@NonNull MarkwonVisitor visitor, @NonNull BulletList list) {
                visitor.blockStart(list);

                final int start = visitor.length();
                visitor.visitChildren(list);

                visitor.setSpansForNodeOptional(list, start);

                final Node next = list.getNext();
                if (next instanceof Paragraph || next instanceof Heading) {
                    visitor.setSpans(start, LastLineSpacingSpan.create(spacingPx));
                }

                visitor.blockEnd(list);
            }
        });
    }

    private void orderedList(@NonNull MarkwonVisitor.Builder builder) {
        builder.on(OrderedList.class, new MarkwonVisitor.NodeVisitor<OrderedList>() {
            @Override
            public void visit(@NonNull MarkwonVisitor visitor, @NonNull OrderedList list) {
                visitor.blockStart(list);

                final int start = visitor.length();
                visitor.visitChildren(list);

                visitor.setSpansForNodeOptional(list, start);

                final Node next = list.getNext();
                if (next instanceof Paragraph || next instanceof Heading) {
                    visitor.setSpans(start, LastLineSpacingSpan.create(spacingPx));
                }

                visitor.blockEnd(list);
            }
        });
    }

    private void listItem(@NonNull MarkwonVisitor.Builder builder) {
        builder.on(ListItem.class, new MarkwonVisitor.NodeVisitor<ListItem>() {
            @Override
            public void visit(@NonNull MarkwonVisitor visitor, @NonNull ListItem listItem) {

                final int start = visitor.length();

                // Important: visit children first (nested lists/items can override props)
                visitor.visitChildren(listItem);

                final Node parent = listItem.getParent();
                if (parent instanceof OrderedList) {
                    final OrderedList orderedList = (OrderedList) parent;
                    final int number = orderedList.getStartNumber();

                    CoreProps.LIST_ITEM_TYPE.set(visitor.renderProps(), CoreProps.ListItemType.ORDERED);
                    CoreProps.ORDERED_LIST_ITEM_NUMBER.set(visitor.renderProps(), number);

                    orderedList.setStartNumber(number + 1);
                } else {
                    CoreProps.LIST_ITEM_TYPE.set(visitor.renderProps(), CoreProps.ListItemType.BULLET);
                    CoreProps.BULLET_LIST_ITEM_LEVEL.set(visitor.renderProps(), listLevel(listItem));
                }

                visitor.setSpansForNodeOptional(listItem, start);

                if (visitor.hasNext(listItem)) {
                    visitor.ensureNewLine();
                    visitor.setSpans(start, LastLineSpacingSpan.create(spacingPx));
                }
            }

            private int listLevel(@NonNull Node node) {
                int level = 0;
                Node p = node.getParent();
                while (p != null) {
                    if (p instanceof ListItem) level++;
                    p = p.getParent();
                }
                return level;
            }
        });
    }

    private boolean isSpacingTargetAfterParagraphOrHeading(Node next) {
        return next instanceof Paragraph
                || next instanceof Heading
                || next instanceof BulletList
                || next instanceof OrderedList;
    }
}

