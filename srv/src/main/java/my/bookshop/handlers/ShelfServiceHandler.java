package my.bookshop.handlers;

import cds.gen.shelfservice.AddBookToShelfContext;
import cds.gen.shelfservice.ShelfService_;
import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ServiceName(ShelfService_.CDS_NAME)
public class ShelfServiceHandler implements EventHandler {

  @Autowired PersistenceService persistenceService;

  @On(event = AddBookToShelfContext.CDS_NAME)
  public void AddBookToShelf(AddBookToShelfContext eventContext) {
    String shelfid = eventContext.getShelfid();
    String bookid = eventContext.getBookid();

    CqnSelect query = Select.from(ShelfService_.SHELVES).where(o -> o.get("ID").eq(shelfid));

    Result persistencyCheck = persistenceService.run(query);

    if (persistencyCheck.first().isPresent()) {
      //  Shelf exists
      CqnSelect bookQuery = Select.from(ShelfService_.BOOKS).where(o -> o.get("ID").eq(bookid));

      Result bookCheck = persistenceService.run(bookQuery);

      if (bookCheck.first().isPresent()) {
        // Book exists

        // Check if ShelfBook entry already exists to prevent duplicates
        CqnSelect shelfBookQuery =
            Select.from(ShelfService_.SHELF_BOOKS)
                .where(o -> o.get("shelf_ID").eq(shelfid).and(o.get("book_ID").eq(bookid)));

        Result shelfBookCheck = persistenceService.run(shelfBookQuery);

        if (shelfBookCheck.first().isPresent()) {
          // Entry already exists, throw conflict error
          throw new ServiceException(
              ErrorStatuses.CONFLICT,
              "Book with ID " + bookid + " is already assigned to Shelf with ID " + shelfid + ".");
        }

        Map<String, Object> bookAssignment = new HashMap<>();
        bookAssignment.put("shelf_ID", shelfid);
        bookAssignment.put("book_ID", bookid);

        CqnInsert insert = Insert.into(ShelfService_.SHELF_BOOKS).entry(bookAssignment);

        persistenceService.run(insert);
      } else {
        // Book does not exist
        throw new ServiceException(
            ErrorStatuses.NOT_FOUND, "Book with ID " + bookid + " does not exist.");
      }
    } else {
      // Shelf does not exist
      throw new ServiceException(
          ErrorStatuses.NOT_FOUND, "Shelf with ID " + shelfid + " does not exist.");
    }
    eventContext.setCompleted();
  }
}
