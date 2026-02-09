namespace my.bookshop;

using {Currency, sap, managed, cuid} from '@sap/cds/common';
using my.bookshop.Books from './books';

entity Shelves: cuid, managed {
    name        : localized String(250);
    maxCapacity : Integer default 10 not null; // default 10 and > 0
    items       : Composition of many ShelfBooks on items.shelf = $self;
}

entity ShelfBooks : cuid, managed {
    shelf : Association to Shelves not null;
    book  : Association to Books not null;
    quantity : Integer default 1 not null;
}

