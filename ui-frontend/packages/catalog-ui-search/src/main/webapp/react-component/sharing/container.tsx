/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
import * as React from 'react'
import SharingPresentation from './presentation'
import fetch from '../utils/fetch/index'
import { Access, Entry, Restrictions, Security } from '../utils/security'
import { EventType } from '../utils/event'

const user = require('component/singletons/user-instance')
const common = require('js/Common')
const announcement = require('component/announcement')
const LoadingView = require('../../component/loading/loading.view')

type Attribute = {
  attribute: string
  values: string[]
}

type Props = {
  id: number
  lightbox: any
  onUpdate?: (attributes: Attribute[]) => void
  type: EventType
}

type State = {
  items: Item[]
  modified: string
  isWorkspace: boolean
  type: EventType
}

export enum Category {
  User = 'user',
  Group = 'group',
}

export type Item = {
  id: string
  value: string
  visible: boolean
  category: Category
  access: Access
}

export const handleRemoveSharedMetacard = async (
  id: number,
  type: EventType
) => {
  const metacard = await fetchMetacard(id)
  const res = Restrictions.from(metacard)
  const security = new Security(res)

  const individuals = security
    .getIndividuals()
    .filter(e => e.value !== user.getUserId())

  const attributes = [
    {
      attribute: Restrictions.IndividualsWrite,
      values: individuals
        .filter(e => e.access === Access.Write)
        .map(e => e.value),
    },
    {
      attribute: Restrictions.IndividualsRead,
      values: individuals
        .filter(e => e.access === Access.Read)
        .map(e => e.value),
    },
    {
      attribute: Restrictions.GroupsWrite,
      values: security
        .getGroups([])
        .filter(e => e.access === Access.Write)
        .map(e => e.value),
    },
    {
      attribute: Restrictions.GroupsRead,
      values: security
        .getGroups([])
        .filter(e => e.access === Access.Read)
        .map(e => e.value),
    },
    {
      attribute: Restrictions.AccessAdministrators,
      values: individuals
        .filter(e => e.access === Access.Share)
        .map(e => e.value),
    },
  ]
  return handleSave(attributes, id, type)
}

const fetchMetacard = async (id: number) => {
  const res = await fetch('/search/catalog/internal/metacard/' + id)
  const metacard = await res.json()
  return metacard.metacards[0]
}

const handleSave = (attributes: any, id: number, type: EventType) => {
  return fetch(`/search/catalog/internal/metacards`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify([
      {
        ids: [id],
        attributes: attributes,
        type: type,
      },
    ]),
  })
}

export class Sharing extends React.Component<Props, State> {
  prevUsers: any
  constructor(props: Props) {
    super(props)
    this.state = {
      items: [],
      modified: '',
      isWorkspace: false,
      type: props.type,
    }
  }
  componentDidMount = () => {
    fetchMetacard(this.props.id).then(data => {
      const metacard = data
      const res = Restrictions.from(metacard)
      const security = new Security(res)
      const individuals = security.getIndividuals().map((e: Entry) => {
        return {
          ...e,
          id: common.generateUUID(),
          category: Category.User,
          visible: e.value !== res.owner, // hide owner
        } as Item
      })
      const groups = security.getGroups(user.getRoles()).map((e: Entry) => {
        return {
          ...e,
          id: common.generateUUID(),
          category: Category.Group,
          visible: user.getRoles().indexOf(e.value) > -1, // only display the groups the current user has
        } as Item
      })
      this.setState({
        items: groups.concat(individuals),
        modified: metacard['metacard.modified'],
        type: data['metacard-tags'].includes('workspace')
          ? EventType.Workspace
          : this.state.type,
      })
      this.add()
    })
  }

  save = () => {
    let usersToUnsubscribe: string[] = []
    const groups = this.state.items.filter(e => e.category === Category.Group)
    const guest = this.state.items.filter(
      e => e.category === Category.Group && e.value === 'guest'
    )
    const users = this.state.items.filter(
      e => e.value !== '' && e.category === Category.User
    )

    if (this.state.type === EventType.Workspace && guest[0].access === 0) {
      usersToUnsubscribe = this.getUsersToUnsubscribe(users)
    }
    const attributes = [
      {
        attribute: Restrictions.IndividualsWrite,
        values: users.filter(e => e.access === Access.Write).map(e => e.value),
      },
      {
        attribute: Restrictions.IndividualsRead,
        values: users.filter(e => e.access === Access.Read).map(e => e.value),
      },
      {
        attribute: Restrictions.GroupsWrite,
        values: groups.filter(e => e.access === Access.Write).map(e => e.value),
      },
      {
        attribute: Restrictions.GroupsRead,
        values: groups.filter(e => e.access === Access.Read).map(e => e.value),
      },
      {
        attribute: Restrictions.AccessAdministrators,
        values: users.filter(e => e.access === Access.Share).map(e => e.value),
      },
    ]

    const loadingView = new LoadingView()
    this.attemptSave(attributes, usersToUnsubscribe)
      .then(() => {
        announcement.announce(
          {
            title: 'Success',
            message: 'Sharing saved',
            type: 'success',
          },
          1500
        )
        this.props.lightbox.close()
      })
      .catch(err => {
        if (err.message === 'concurrent-modification') {
          announcement.announce(
            {
              title: 'The workspace settings could not be updated',
              message:
                'The workspace has been modified by another user. Please refresh the page and reattempt your changes.',
              type: 'error',
            },
            1500
          )
        } else {
          announcement.announce(
            {
              title: 'Error',
              message: 'Save failed',
              type: 'error',
            },
            1500
          )
        }
      })
      .then(() => {
        loadingView.remove()
      })
  }

  // NOTE: Fetching the latest metacard and checking the modified dates is a temporary solution
  // and should be removed when support for optimistic concurrency is added
  // https://github.com/codice/ddf/issues/4467
  attemptSave = async (attributes: any, usersToUnsubscribe: String[]) => {
    const currMetacard = await fetchMetacard(this.props.id)
    if (currMetacard['metacard.modified'] === this.state.modified) {
      await this.doSave(attributes)
      await this.unsubscribeUsers(usersToUnsubscribe)
      const newMetacard = await fetchMetacard(this.props.id)
      this.setState({
        items: [...this.state.items],
        modified: newMetacard['metacard.modified'],
      })
    } else {
      throw new Error('concurrent-modification')
    }
  }

  doSave = async (attributes: any) => {
    const res = await handleSave(attributes, this.props.id, this.props.type)

    if (res.status !== 200) {
      throw new Error()
    }

    if (this.props.onUpdate) {
      this.props.onUpdate(attributes)
    }
    return await res.json()
  }

  unsubscribeUsers = async (usersToUnsubscribe: String[]) => {
    if (usersToUnsubscribe.length === 0) {
      return
    }
    const res = await fetch(
      '/search/catalog/internal/unsubscribe/' + this.props.id,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          attribute: 'unsubscribedUsers',
          values: usersToUnsubscribe,
        }),
      }
    )
    return await res.json()
  }

  getUsersToUnsubscribe(users: Item[]) {
    let usersToUnsubscribe: string[] = []
    const usersWithReadOrHigher = users.filter(e => e.access !== 0)
    if (this.prevUsers === undefined) {
      this.prevUsers = usersWithReadOrHigher
    } else if (this.prevUsers !== usersWithReadOrHigher) {
      this.prevUsers.forEach((user: Item) => {
        if (!usersWithReadOrHigher.includes(user)) {
          usersToUnsubscribe.push(user.value)
        }
      })
      this.prevUsers = usersWithReadOrHigher
    }
    return usersToUnsubscribe
  }

  add = () => {
    this.state.items.push({
      id: common.generateUUID(),
      value: '',
      visible: true,
      category: Category.User,
      access: Access.Read,
    })
    this.setState({
      items: this.state.items,
    })
  }

  remove = (i: number) => {
    this.state.items.splice(i, 1)
    this.setState({
      items: this.state.items,
    })
  }

  handleChangeInput = (i: number, value: string) => {
    this.state.items[i].value = value.toLowerCase()
    this.setState({
      items: this.state.items,
    })
  }

  handleChangeSelect = (i: number, value: Access) => {
    this.state.items[i].access = value
    this.setState({
      items: this.state.items,
    })
  }

  reset = () => {
    // resetting to a saved initial state is the preferred style, but
    // the react wrappers do not currently support updating state properly
    this.componentDidMount()
  }

  render() {
    return (
      <SharingPresentation
        items={this.state.items}
        add={this.add}
        save={this.save}
        reset={this.reset}
        remove={this.remove}
        handleChangeSelect={this.handleChangeSelect}
        handleChangeInput={this.handleChangeInput}
      />
    )
  }
}
