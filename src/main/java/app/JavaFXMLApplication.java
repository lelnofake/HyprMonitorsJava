package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class JavaFXMLApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader= new  FXMLLoader(getClass().getResource("/view.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);

        stage.setScene(scene);
        stage.setTitle("hyprmonitors");
        stage.setResizable(true);
        stage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
