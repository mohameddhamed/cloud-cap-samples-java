package my.bookshop.handlers;

import cds.gen.shelfservice.*;
import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnComparisonPredicate;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@ServiceName(ShelfService_.CDS_NAME)
public class ShelfServiceHandler implements EventHandler {

  @Autowired PersistenceService persistenceService;

  @Autowired
  @Lazy
  @Qualifier(ShelfService_.CDS_NAME)
  CqnService shelfService;

  //  @Autowired
  //  ServiceCatalog serviceCatalog;
  //  private CqnService shelfService;
  @Before(event = CqnService.EVENT_UPDATE, entity = Shelves_.CDS_NAME)
  public void beforeUpdateShelf(Shelves shelf, CdsUpdateEventContext context) {

    String shelfId = shelf.getId();
    if (shelfId != null) {
      CqnSelect query = Select.from(ShelfService_.SHELVES).where(o -> o.get("ID").eq(shelfId));

      Result result = persistenceService.run(query);

      if (result.first().isEmpty()) {
        throw new ServiceException(
            ErrorStatuses.NOT_FOUND, "Shelf with ID " + shelfId + " does not exist.");
      }
    }
    //    1.	Name is mandatory
    //    2.	Capacity is also mandatory, default should be 10 and cannot be negative.
    if (shelf.getCapacity() == null && shelf.getName() == null) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "You must provide at least one of the following properties to update a shelf: name, capacity.");
    }

    if (shelf.getName() != null) {
      // We are in the case where only the name is being updated, we need to check if the new name
      // already exists to prevent duplicates
      CqnSelect query =
          Select.from(ShelfService_.SHELVES).where(o -> o.get("name").eq(shelf.getName()));

      Result result = persistenceService.run(query);

      if (result.first().isPresent()) {
        throw new ServiceException(
            ErrorStatuses.CONFLICT, "Shelf with name " + shelf.getName() + " already exists.");
      }
    }
    // handle capacity update case: if capacity is updated, ensure that the new capacity is not less
    // than the number of books currently assigned to the shelf
    System.out.println("Shelf Id from Shelves object: " + shelf.getId());
    if (shelf.getCapacity() != null) {
      CqnSelect shelfBookQuery =
          Select.from(ShelfService_.SHELF_BOOKS)
              .columns(CQL.count().as("total"))
              .where(sb -> sb.get("shelf_ID").eq(shelfId));
      Result shelfBookResult = persistenceService.run(shelfBookQuery);
      long currentBookAmount = (long) shelfBookResult.single().get("total");
      if (shelf.getCapacity() < currentBookAmount) {
        throw new ServiceException(
            ErrorStatuses.BAD_REQUEST,
            "Shelf capacity cannot be less than the number of books currently assigned to the shelf. Current book amount: "
                + currentBookAmount);
      }
    }

    // handle deep insert case: if books are assigned during shelf creation, ensure capacity is not
    // exceeded
    List<ShelfBooks> bookAssignments = shelf.getBookAssignments();
    if (bookAssignments != null && bookAssignments.size() > shelf.getCapacity()) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "Number of books assigned to the shelf cannot exceed its capacity.");
    }
  }

  @Before(event = CqnService.EVENT_CREATE, entity = Shelves_.CDS_NAME)
  public void beforeCreateShelf(Shelves shelf) {
    //    1.	Name is mandatory
    //    2.	Capacity is also mandatory, default should be 10 and cannot be negative.
    if (shelf.getName() == null || shelf.getName().isEmpty()) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Shelf name is mandatory.");
    }

    CqnSelect query =
        Select.from(ShelfService_.SHELVES).where(o -> o.get("name").eq(shelf.getName()));

    Result result = persistenceService.run(query);

    if (result.first().isPresent()) {
      throw new ServiceException(
          ErrorStatuses.CONFLICT, "Shelf with name " + shelf.getName() + " already exists.");
    }

    if (shelf.getCapacity() == null) {
      shelf.setCapacity(10); // Set default capacity
    } else if (shelf.getCapacity() < 0) {
      throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Shelf capacity cannot be negative.");
    }

    // handle deep insert case: if books are assigned during shelf creation, ensure capacity is not
    // exceeded
    List<ShelfBooks> bookAssignments = shelf.getBookAssignments();
    if (bookAssignments != null && bookAssignments.size() > shelf.getCapacity()) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "Number of books assigned to the shelf cannot exceed its capacity.");
    }
  }

  //  2.	Books
  // 1.	There should be no more books on a shelf than the capacity
  // 1.	If you create a shelf w/ a deep insert (see below), don’t allow adding in more books than
  // capacity
  // 2.	If you just add new books, stop addition if you reach capacity.
  // 3.	If you update the capacity, ensure that there the number of books on the shelf still fits.
  // (There are 5 books assigned, you cannot set capacity from 5 to 3 for example).
  //
  @Before(event = CqnService.EVENT_CREATE, entity = ShelfBooks_.CDS_NAME)
  public void beforeCreateShelfBook(ShelfBooks shelfBook, CdsCreateEventContext context) {

    System.out.println(">>> beforeCreateShelfBook FIRED");
    System.out.println(">>> entries: " + context.getCqn().entries());

    var segments = context.getCqn().ref().segments();
    var filter = segments.isEmpty() ? null : segments.get(0).filter().orElse(null);

    System.out.println(">>> segments: " + (segments.isEmpty() ? "empty" : segments.get(0)));
    System.out.println(">>> filter: " + filter);
    System.out.println(
        ">>> filter class: " + (filter != null ? filter.getClass().getName() : "null"));

    String tempShelfId = null;

    if (filter instanceof CqnComparisonPredicate comparison) {
      tempShelfId = comparison.right().asLiteral().value().toString();
      System.out.println(">>> shelfId from filter (/bookAssignment): " + tempShelfId);
    }

    if (tempShelfId == null || tempShelfId.isEmpty()) {
      tempShelfId = context.getCqn().entries().get(0).get(ShelfBooks_.SHELF_ID).toString();
      System.out.println(">>> shelfId from action: " + tempShelfId);
    }

    final String shelfId = tempShelfId;
    System.out.println(">>> bookId: " + shelfBook.getBookId());
    System.out.println(">>> final shelfId: " + shelfId);

    // Checking if book even exists
    CqnSelect bookQuery =
        Select.from(ShelfService_.BOOKS).where(o -> o.get("ID").eq(shelfBook.getBookId()));
    Result bookResult = persistenceService.run(bookQuery);
    if (bookResult.first().isEmpty()) {
      throw new ServiceException(
          ErrorStatuses.NOT_FOUND, "Book with ID " + shelfBook.getBookId() + " does not exist.");
    }

    // Checking if book and shelf association already exists to prevent duplicates
    CqnSelect shelfBookAssociationQuery =
        Select.from(ShelfService_.SHELF_BOOKS)
            .where(
                o -> o.get("shelf_ID").eq(shelfId).and(o.get("book_ID").eq(shelfBook.getBookId())));
    Result shelfBookAssociationResult = persistenceService.run(shelfBookAssociationQuery);
    if (shelfBookAssociationResult.first().isPresent()) {
      throw new ServiceException(
          ErrorStatuses.CONFLICT,
          "Book with ID "
              + shelfBook.getBookId()
              + " is already assigned to Shelf with ID "
              + tempShelfId
              + ".");
    }

    // I need to query the ShelfBooks to see how many rows have shelfId = shelfId
    // That number is the current amount of books on the shelf. I also need to query the Shelves to
    // get the capacity of the shelf.
    // I can't use the association because it's loaded lazily

    // Getting the count of bookAssignments with the given shelfId
    CqnSelect shelfBookQuery =
        Select.from(ShelfService_.SHELF_BOOKS)
            .columns(CQL.count().as("total")) // The magic part
            .where(sb -> sb.get("shelf_ID").eq(shelfId));
    Result shelfBookResult = persistenceService.run(shelfBookQuery);
    long currentBookAmount = (long) shelfBookResult.single().get("total");

    System.out.println(">>> currentBookAmount: " + currentBookAmount);

    // Getting the capacity of the shelf
    CqnSelect shelfQuery = Select.from(ShelfService_.SHELVES).where(o -> o.get("ID").eq(shelfId));
    Result shelfResult = persistenceService.run(shelfQuery);
    if (shelfResult.first().isEmpty()) {
      // This means that we are in a deep insert scenario where the shelf is being created at the
      // same time as the book assignment
      // so the capacity validation is handled by the parent
      System.out.println(
          ">>> shelf result is empty, assuming deep insert scenario, skipping capacity validation");
      return;
    }
    // shelf Found -> this is not a deep insert, rather a POST /Shelves(ID)/bookAssignments
    // so the capacity validation needs to be handled here
    Shelves shelf = shelfResult.single(Shelves.class);
    int maximumBookAmount = shelf.getCapacity();

    System.out.println(">>> maximumBookAmount: " + maximumBookAmount);

    // This works because each bookAssignment counts for 1 book
    if (maximumBookAmount < currentBookAmount + 1) {
      throw new ServiceException(
          ErrorStatuses.BAD_REQUEST,
          "Cannot add book to shelf. Shelf capacity of "
              + maximumBookAmount
              + " would be exceeded.");
    }
    System.out.println(
        "Successfully passed capacity validation, allowing book to be added to shelf.");
  }

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
        shelfService.run(insert);

        //        persistenceService.run(insert);
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
