using {my.bookshop as my} from '../db/index';

@path: 'shelf'
service ShelfService {
  entity Shelves as projection on my.Shelves;

  entity ShelfBooks as projection on my.ShelfBooks;

  @readonly
  entity Books      as projection on my.Books;

}