package monitors;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.List;

public class monitors {
    private String name;
    private int width, height;
    private int x, y;
    private int transform;
    private List<String> avaliableModes;

    private Rectangle rectangle;
    private Rectangle bottom;
    private Group group;

    public monitors (String name, int width, int height,  int x, int y, int transform) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.x = x;
        this.y = y;
        this.transform = transform;

        rectangle = new Rectangle(width, height, Color.LIGHTBLUE);
        rectangle.setStroke(Color.DARKBLUE);
        rectangle.setStrokeWidth(2);

        bottom = new Rectangle(width, 2, Color.BLACK);
        bottom.setStroke(Color.BLACK);
        bottom.setTranslateY(height - 2);
        bottom.setStrokeWidth(2);

        group = new Group(rectangle, bottom);
        group.setTranslateX(x);
        group.setTranslateY(y);
        group.setRotate(transform * 90);
    }

    public Group getGroup() {return group;}

    public void setPosition(int nx, int ny) {
        this.x = nx;
        this.y = ny;
        group.setTranslateX(x);
        group.setTranslateY(y);
    }

    public void setTransform(int ntransform) {
        this.transform = ntransform;
        group.setRotate(transform * 90);
    }

    public int getTransform() {return transform;}
    public int getWidth() {return width;}
    public int getHeight() {return height;}
    public int getX() {return x;}
    public int getY() {return y;}
    public String getName() {return name;}
}
